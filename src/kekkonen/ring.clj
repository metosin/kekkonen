(ns kekkonen.ring
  (:require [schema.core :as s]
            [ring.swagger.coerce :as rsc]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [clojure.string :as str]
            [ring.swagger.json-schema :as rsjs]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]
            [plumbing.map :as pm]
            [kekkonen.interceptor :as interceptor])
  (:import [kekkonen.core Dispatcher]))

(def ^:private mode-parameter "kekkonen.mode")

(s/defschema Options
  {:types {s/Keyword {:methods #{s/Keyword}
                      (s/optional-key :parameters) {[s/Keyword] [s/Keyword]}
                      (s/optional-key :allow-method-override?) s/Bool}}
   :coercion {s/Keyword k/Function}
   :interceptors [k/InterceptorLike]})

(s/def +default-options+ :- Options
  ; TODO: no types in default bindings?
  ; TODO: add type-resolver?
  {:types {::handler {:methods #{:post}
                      :allow-method-override? true
                      :parameters {[:data] [:request :body-params]}}
           :handler {:methods #{:post}
                     :parameters {[:data] [:request :body-params]}}}
   :coercion {:query-params rsc/query-schema-coercion-matcher
              :path-params rsc/query-schema-coercion-matcher
              :form-params rsc/query-schema-coercion-matcher
              :header-params rsc/query-schema-coercion-matcher
              :multipart-params rsc/query-schema-coercion-matcher
              :body-params rsc/json-schema-coercion-matcher}
   :interceptors []})

(def +ring-dispatcher-options+
  {:coercion {:input nil
              :output nil}
   :meta {::disable-mode nil
          ::method nil
          ::consumes nil
          ::produces nil
          :responses nil}})

;;
;; Internals
;;

(defn- uri->action [^String path]
  (let [i (.lastIndexOf path "/")]
    (if-not (= (count path) 1)
      (keyword (subs (str (str/replace (subs path 0 i) #"/" ".") (subs path i)) 1)))))

(defn- handler-uri [handler]
  (str
    (if-let [ns (some-> handler :ns name)]
      (str "/" (str/replace ns #"\." "/")))
    "/" (name (:name handler))))

(defn- ring-coercion [parameters coercion]
  (if coercion
    (let [coercions (pm/unflatten
                      (for [[k matcher] coercion
                            :when matcher]
                        [[:request k] (fn [schema value]
                                        (k/coerce! schema matcher (or value {}) k ::request))]))]
      (k/coercion
        (if parameters
          (reduce kc/copy-to-from coercions parameters)
          coercions)))))

(defn- ring-input-schema [input parameters]
  (if parameters
    (reduce kc/move-from-to input parameters)
    input))

(defn- attach-mode-parameter [schema]
  (let [key (s/optional-key mode-parameter)
        value (rsjs/describe (s/enum "invoke" "validate") "mode" :default "invoke")
        extra-keys-schema (s/find-extra-keys-schema (get-in schema [:request :header-params]))]
    (update-in schema [:request :header-params] merge {key value} (if-not extra-keys-schema {s/Any s/Any}))))

(defn- request-mode [request]
  (if (= "validate" (-> request :headers (get mode-parameter)))
    :validate :invoke))

(defn- attach-ring-meta [options handler]
  (let [{:keys [parameters allow-method-override?] :as type-config} (get (:types options) (:type handler))
        coercion (:coercion options)
        method (some-> handler :meta ::method)
        methods (if (and allow-method-override? method)
                  (conj #{} method)
                  (:methods type-config))
        input-schema (-> (:input handler)
                         (ring-input-schema parameters)
                         (cond-> (not (get-in handler [:meta ::disable-mode])) attach-mode-parameter))
        meta {:type-config type-config
              :methods methods
              :coercion (ring-coercion parameters coercion)
              :uri (handler-uri handler)
              :input input-schema}]
    (assoc handler :ring meta)))

(defn- uri-without-context
  "Extracts the uri from the request but dropping the context"
  [{:keys [^String uri ^String context]}]
  (if (and context (.startsWith uri context))
    (.substring uri (.length context))
    uri))

(defn- copy-parameters [ctx parameters]
  (reduce-kv
    (fn [acc to from]
      (let [value (get-in acc from)]
        (assoc-in acc to value)))
    ctx
    parameters))

(defn- prepare [handler]
  (let [parameters (-> handler :ring :type-config :parameters)
        coercion (-> handler :ring :coercion)]
    {:enter (fn [ctx]
              (-> ctx
                  (assoc ::k/coercion coercion)
                  (copy-parameters parameters)))}))

(defn- clean-context [context]
  (-> context
      (kc/dissoc-in [:request :query-params :kekkonen.action])
      (kc/dissoc-in [:request :query-params :kekkonen.mode])
      (kc/dissoc-in [:request :query-params :kekkonen.ns])))

;;
;; Response Coercion
;;

(defn- get-response-schema [responses status]
  (or (-> responses (get status) :schema)
      (-> responses :default :schema)))

(defn- coerce-response [response responses matcher]
  (let [status (get response :status 200)]
    (if-let [schema (get-response-schema responses status)]
      (let [coerced (k/coerce! schema matcher (:body response) :response ::response)]
        (assoc response :body coerced))
      response)))

(defn- response-coercer [handler options]
  (let [responses (-> handler :meta :responses)
        coerce #(coerce-response % responses options)]
    {:leave (fn [ctx]
              (if (not= :invoke (::k/mode ctx))
                (update ctx :response ok)
                (update ctx :response coerce)))}))

;;
;; Special endpoints
;;

(def +kekkonen-handlers+
  {:kekkonen
   [(k/handler
      {:name "handler"
       :type ::handler
       ::disable-mode true
       ::method :get
       :input {:request
               {:query-params
                {(s/optional-key :kekkonen.action) s/Keyword
                 s/Keyword s/Any}
                s/Keyword s/Any}
               s/Keyword s/Any}
       :description "Returns a handler info or nil."
       :handle (fn [{{{action :kekkonen.action} :query-params} :request :as context}]
                 (ok (k/public-handler
                       (k/some-handler
                         (k/get-dispatcher context)
                         action))))})
    (k/handler
      {:name "handlers"
       :type ::handler
       ::disable-mode true
       ::method :get
       :input {:request
               {:query-params
                {(s/optional-key :kekkonen.ns) s/Keyword
                 s/Keyword s/Any}
                s/Keyword s/Any}
               s/Keyword s/Any}
       :description "Return a list of available handlers from kekkonen.ns namespace"
       :handle (fn [{{{ns :kekkonen.ns} :query-params} :request :as context}]
                 (ok (-> context
                         k/get-dispatcher
                         (k/available-handlers ns (clean-context context))
                         (->> (filter (p/fn-> :ring))
                              (remove (p/fn-> :ns (= :kekkonen)))
                              (remove (p/fn-> :meta :no-doc))
                              (map k/public-handler)))))})
    (k/handler
      {:name "actions"
       :type ::handler
       ::disable-mode true
       ::method :post
       :input {:request
               {:body-params {s/Keyword s/Any}
                :query-params
                {(s/optional-key :kekkonen.ns) s/Keyword
                 (s/optional-key :kekkonen.mode) (with-meta
                                                   (s/enum :check :validate)
                                                   {:json-schema {:default :check}})
                 s/Keyword s/Any}
                s/Keyword s/Any}
               s/Keyword s/Any}
       :description "Return a map of action -> error of all available handlers"
       :handle (fn [{{{mode :kekkonen.mode ns, :kekkonen.ns} :query-params} :request :as context}]
                 (ok (-> context
                         k/get-dispatcher
                         (k/dispatch-handlers (or mode :check) ns (clean-context context))
                         (->> (filter (p/fn-> first :ring))
                              (remove (p/fn-> first :ns (= :kekkonen)))
                              (remove (p/fn-> first :meta :no-doc))
                              (map (fn [[k v]] [(:action k) (k/stringify-schema v)]))
                              (into {})))))})]})

;;
;; Public api
;;

(s/defn ring-handler
  "Creates a ring handler from Dispatcher and options."
  ([dispatcher :- Dispatcher]
    (ring-handler dispatcher {}))
  ([dispatcher :- Dispatcher, options :- k/KeywordMap]
    (let [options (-> (kc/deep-merge-map-like +default-options+ options)
                      (update :interceptors (partial mapv k/interceptor)))
          dispatcher (k/transform-handlers dispatcher (partial attach-ring-meta options))
          router (p/for-map [handler (k/all-handlers dispatcher nil)
                             :let [interceptors (kc/join
                                                  (prepare handler)
                                                  (:interceptors options)
                                                  (response-coercer handler options))]]
                   (-> handler :ring :uri) [handler interceptors])]
      ;; the ring handler
      (fn [{:keys [request-method] :as request}]
        ;; match a handlers based on uri and context
        (if-let [[{:keys [action ring]} interceptors] (router (uri-without-context request))]
          ;; only allow calls to ring-mapped handlers with matching method
          (if ((:methods ring) request-method)
            (let [mode (request-mode request)]
              (-> {:request request}
                  (interceptor/enqueue interceptors)
                  (->> (k/dispatch dispatcher mode action))))))))))

;;
;; Routing
;;

(s/defn routes :- k/Function
  "Creates a ring handler of multiples handlers, matches in order."
  [ring-handlers :- [(s/maybe k/Function)]]
  (apply some-fn (keep identity ring-handlers)))

(s/defn match
  "Creates a ring-handler for given uri & request-method"
  ([match-uri ring-handler]
    (match match-uri identity ring-handler))
  ([match-uri match-request-method ring-handler]
    (fn [{:keys [uri request-method] :as request}]
      (if (and (= match-uri uri)
               (match-request-method request-method))
        (ring-handler request)))))
