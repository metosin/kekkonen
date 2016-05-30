(ns kekkonen.ring
  (:require [schema.core :as s]
            [ring.swagger.coerce :as rsc]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [clojure.string :as str]
            [ring.swagger.json-schema :as rsjs]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]
            [plumbing.map :as pm])
  (:import [kekkonen.core Dispatcher]))

(def ^:private mode-parameter "kekkonen.mode")

(s/defschema Options
  {:types {s/Keyword {:methods #{s/Keyword}
                      (s/optional-key :parameters) {[s/Keyword] [s/Keyword]}
                      (s/optional-key :allow-method-override?) s/Bool}}
   :coercion {s/Keyword k/Function}
   :interceptors [k/FunctionOrInterceptor]})

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
              :body-params rsc/json-schema-coercion-matcher}
   :interceptors []})

(def +ring-meta+
  {::disable-mode nil
   ::method nil})

;;
;; Internals
;;

(defn- uri->action [path]
  (let [i (.lastIndexOf path "/")]
    (if-not (= (count path) 1)
      (keyword (subs (str (str/replace (subs path 0 i) #"/" ".") (subs path i)) 1)))))

(defn- handler-uri [handler]
  (str
    (if-let [ns (some-> handler :ns name)]
      (str "/" (str/replace ns #"\." "/")))
    "/" (name (:name handler))))

(defn- ring-coercion [parameters coercion]
  (let [coercions (pm/unflatten
                    (for [[k matcher] coercion
                          :when matcher]
                      [[:request k] (fn [schema value]
                                      (k/coerce! schema matcher (or value {}) k ::request))]))]
    (k/multi-coercion
      (if parameters
        (reduce kc/copy-to-from coercions parameters)
        coercions))))

(defn- coerce-response! [response handler options]
  (if-let [responses (-> handler :meta :responses)]
    (let [status (or (:status response) 200)
          schema (get-in responses [status :schema])]
      (if schema
        (let [value (:body response)
              matcher (get-in options [:coercion :body-params])
              coerced (k/coerce! schema matcher value :response ::response)]
          (assoc response :body coerced))
        response))
    response))

(defn- ring-input-schema [input parameters]
  (if parameters
    (reduce kc/move-from-to input parameters)
    input))

(defn- attach-mode-parameter [schema]
  (let [key (s/optional-key mode-parameter)
        value (rsjs/describe (s/enum "invoke" "validate") "mode" :default "invoke")
        extra-keys-schema (s/find-extra-keys-schema (get-in schema [:request :header-params]))]
    (update-in schema [:request :header-params] merge {key value} (if-not extra-keys-schema {s/Any s/Any}))))

(defn- is-validate-request? [request]
  (= (get-in request [:headers mode-parameter]) "validate"))

(defn- attach-ring-meta [options handler]
  (let [{:keys [parameters allow-method-override?] :as type-config} (get (:types options) (:type handler))
        coercion (:coercion options)
        method (some-> handler :meta ::method)
        methods (if (and allow-method-override? method)
                  (conj #{} method)
                  (:methods type-config))
        input-schema (-> (:input handler)
                         (ring-input-schema parameters)
                         (cond-> (not (get-in handler [:meta ::disable-mode])) attach-mode-parameter))]
    (assoc handler :ring {:type-config type-config
                          :methods methods
                          :coercion (ring-coercion parameters coercion)
                          :uri (handler-uri handler)
                          :input input-schema})))

(defn- uri-without-context
  "Extracts the uri from the request but dropping the context"
  [{:keys [uri context]}]
  (if (and context (.startsWith uri context))
    (.substring uri (.length context))
    uri))

(defn- copy-parameters [handler ctx]
  (reduce kc/deep-merge-to-from ctx (-> handler :ring :type-config :parameters)))

(defn- set-coercion [handler ctx]
  (assoc ctx ::k/coercion (-> handler :ring :coercion)))

(defn- prepare [dispatcher handler]
  {:enter (fn [ctx]
            (->> ctx
                 (k/prepare dispatcher handler)
                 (set-coercion handler)
                 (copy-parameters handler)))})

(defn- dispatch [options]
  {:enter (fn [{:keys [request ::k/dispatcher] {:keys [action] :as handler} ::k/handler :as ctx}]
            (let [response (if (is-validate-request? request)
                             (ok (k/validate dispatcher action ctx))
                             (let [response (k/invoke dispatcher action ctx)]
                               (coerce-response! response handler options)))]
              (assoc ctx :response response)))})

(defn- clean-context [context]
  (-> context
      (kc/dissoc-in [:request :query-params :kekkonen.action])
      (kc/dissoc-in [:request :query-params :kekkonen.mode])
      (kc/dissoc-in [:request :query-params :kekkonen.ns])))

;;
;; Public api
;;

(s/defn ring-handler
  "Creates a ring handler from Dispatcher and options."
  ([dispatcher :- Dispatcher]
    (ring-handler dispatcher {}))
  ([dispatcher :- Dispatcher, options :- k/KeywordMap]
    (let [options (-> (kc/deep-merge +default-options+ options)
                      (update :interceptors (partial mapv k/interceptor)))
          dispatcher (k/transform-handlers dispatcher (partial attach-ring-meta options))
          router (p/for-map [handler (k/all-handlers dispatcher nil)] (-> handler :ring :uri) handler)]
      (fn [{:keys [request-method] :as request}]
        ;; match a handlers based on uri and context
        (if-let [handler (router (uri-without-context request))]
          ;; only allow calls to ring-mapped handlers with matching method
          (if (some-> handler :ring :methods (contains? request-method))
            (let [interceptors (kc/join
                                 (prepare dispatcher handler)
                                 (:interceptors options)
                                 (dispatch options))]
              (-> {:request request}
                  (k/execute interceptors)
                  :response))))))))

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

;;
;; Special handlers
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
                 (ok (->> context
                          k/get-dispatcher
                          (p/<- (k/available-handlers ns (clean-context context)))
                          (filter (p/fn-> :ring))
                          (remove (p/fn-> :ns (= :kekkonen)))
                          (remove (p/fn-> :meta :no-doc))
                          (map k/public-handler))))})
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
                 (ok (->> context
                          k/get-dispatcher
                          (p/<- (k/dispatch-handlers (or mode :check) ns (clean-context context)))
                          (filter (p/fn-> first :ring))
                          (remove (p/fn-> first :ns (= :kekkonen)))
                          (remove (p/fn-> first :meta :no-doc))
                          (map (fn [[k v]] [(:action k) (k/stringify-schema v)]))
                          (into {}))))})]})
