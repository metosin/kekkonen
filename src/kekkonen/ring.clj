(ns kekkonen.ring
  (:require [schema.core :as s]
            [ring.swagger.coerce :as rsc]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [clojure.string :as str]
            [ring.swagger.json-schema :as rsjs]
            [clojure.walk :as walk]
            [plumbing.core :as p]))

(def ^:private mode-parameter "kekkonen.mode")

(s/defschema Options
  {:types {s/Keyword {:methods #{s/Keyword}
                      (s/optional-key :parameters) [[(s/one [s/Keyword] 'from)
                                                     (s/one [s/Keyword] 'to)]]
                      (s/optional-key :transformers) [k/Function]}}
   :coercion {s/Keyword k/Function}
   :transformers [k/Function]})

(s/def +default-options+ :- Options
  ; TODO: no types in default bindings?
  {:types {::handler {:methods #{:get :head :patch :delete :options :post :put}}
           :handler {:methods #{:post}
                     :parameters [[[:request :body-params] [:data]]]}}
   :coercion {:query-params rsc/query-schema-coercion-matcher
              :path-params rsc/query-schema-coercion-matcher
              :form-params rsc/query-schema-coercion-matcher
              :header-params rsc/query-schema-coercion-matcher
              :body-params rsc/json-schema-coercion-matcher}
   :transformers []})

(s/defn uri->action :- s/Keyword
  "Converts an action keyword from a uri string."
  [path :- s/Str]
  (let [i (.lastIndexOf path "/")]
    (if-not (= (count path) 1)
      (keyword (subs (str (str/replace (subs path 0 i) #"/" ".") (subs path i)) 1)))))

(s/defn handler-uri :- s/Str
  "Creates a uri for the handler"
  [handler :- k/Handler]
  (str
    (if-let [ns (some-> handler :ns name)]
      (str "/" (str/replace ns #"\." "/")))
    "/" (name (:name handler))))

(defn coerce-request!
  "Coerces a request against a handler ring input schema based on :coercion options."
  [request handler {:keys [coercion]}]
  (reduce
    (fn [request [k matcher]]
      (if matcher
        (if-let [schema (get-in handler [:ring :input :request k])]
          (let [value (get request k {})
                coerced (k/coerce! schema matcher value k ::request)]
            (if-not (empty? value)
              (assoc request k coerced)
              request))
          request)
        request))
    request
    coercion))

(defn- ring-input-schema [input parameters]
  (if parameters
    (reduce kc/move-to-from input parameters)
    input))

(defn- attach-mode-parameter [schema]
  (let [key (s/optional-key mode-parameter)
        value (rsjs/describe (s/enum "invoke" "validate") "mode" :default "invoke")
        extra-keys-schema (s/find-extra-keys-schema (get-in schema [:request :header-params]))]
    (update-in schema [:request :header-params] merge {key value} (if-not extra-keys-schema {s/Any s/Any}))))

(defn is-validate-request? [request]
  (= (get-in request [:headers mode-parameter]) "validate"))

(s/defn attach-ring-meta
  [options :- Options, handler :- k/Handler]
  (let [type-config (get (:types options) (:type handler))
        input-schema (-> (:input handler)
                         (ring-input-schema (:parameters type-config))
                         attach-mode-parameter)]
    (assoc handler :ring {:type-config type-config
                          :uri (handler-uri handler)
                          :input input-schema})))

(s/defn ring-handler
  "Creates a ring handler from Dispatcher and options."
  ([dispatcher :- k/Dispatcher]
    (ring-handler dispatcher {}))
  ([dispatcher :- k/Dispatcher, options :- k/KeywordMap]
    (let [options (kc/deep-merge +default-options+ options)
          dispatcher (k/transform-handlers dispatcher (partial attach-ring-meta options))
          router (p/for-map [handler (k/get-handlers dispatcher :all nil)] (-> handler :ring :uri) handler)]
      (fn [{:keys [request-method uri] :as request}]
        (if-let [handler (router uri)]
          (if-let [type-config (-> handler :ring :type-config)]
            (if (get (:methods type-config) request-method)
              (let [action (:action handler)
                    request (coerce-request! request handler options)
                    context (as-> {:request request} context
                                  ;; global transformers first
                                  (reduce (fn [ctx mapper] (mapper ctx)) context (:transformers options))
                                  ;; type-level transformers
                                  (reduce (fn [ctx mapper] (mapper ctx)) context (:transformers type-config))
                                  ;; map parameters from ring-request into common keys
                                  (reduce kc/deep-merge-from-to context (:parameters type-config)))]
                (if (is-validate-request? request)
                  {:status 200, :headers {}, :body (k/validate dispatcher action context)}
                  (let [response (k/invoke dispatcher action context)]
                    (if-let [responses (-> handler :user :responses)]
                      (let [status (or (:status response) 200)
                            schema (get-in responses [status :schema])
                            matcher (get-in options [:coercion :body-params])
                            value (:body response)]
                        (if schema
                          (let [coerced (k/coerce! schema matcher value :response ::response)]
                            (assoc response :body coerced))
                          response))
                      response)))))))))))

(s/defn routes :- k/Function
  "Creates a ring handler of multiples handlers, matches in orcer."
  [ring-handlers :- [k/Function]]
  (apply some-fn ring-handlers))

(s/defn match
  ([match-uri ring-handler]
    (match match-uri identity ring-handler))
  ([match-uri match-request-method ring-handler]
    (fn [{:keys [uri request-method] :as request}]
      (if (and (= match-uri uri)
               (match-request-method request-method))
        (ring-handler request)))))

(s/defn keywordize-keys
  "Returns a function, that keywordizes keys in a given path in context"
  [in :- [s/Any]]
  (fn [context]
    (update-in context in walk/keywordize-keys)))
