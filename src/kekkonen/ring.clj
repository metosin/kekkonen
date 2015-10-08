(ns kekkonen.ring
  (:require [schema.core :as s]
            [ring.swagger.coerce :as rsc]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [clojure.string :as str]
            [ring.swagger.json-schema :as rsjs]
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
  ; TODO: add type-resolver?
  {:types {::handler {:methods #{:get :head :patch :delete :options :post :put}}
           :handler {:methods #{:post}
                     :parameters [[[:request :body-params] [:data]]]}}
   :coercion {:query-params rsc/query-schema-coercion-matcher
              :path-params rsc/query-schema-coercion-matcher
              :form-params rsc/query-schema-coercion-matcher
              :header-params rsc/query-schema-coercion-matcher
              :body-params rsc/json-schema-coercion-matcher}
   :transformers []})

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

(defn- lazy-coerce-request! [request handler {:keys [coercion]}]
  (reduce
    (fn [request [k matcher]]
      (if matcher
        (if-let [schema (get-in handler [:ring :input :request k])]
          (let [value (get request k {})]
            (kc/lazy-assoc-in request [k] (fn [] (k/coerce! schema matcher value k ::request))))
          request)
        request))
    (kc/lazy-map request)
    coercion))

(defn- coerce!-response [response handler options]
  (if-let [responses (-> handler :user :responses)]
    (let [status (or (:status response) 200)
          schema (get-in responses [status :schema])
          matcher (get-in options [:coercion :body-params])
          value (:body response)]
      (if schema
        (let [coerced (k/coerce! schema matcher value :response ::response)]
          (assoc response :body coerced))
        response))
    response))

(defn- ring-input-schema [input parameters]
  (if parameters
    (reduce kc/move-to-from input parameters)
    input))

(defn- attach-mode-parameter [schema]
  (let [key (s/optional-key mode-parameter)
        value (rsjs/describe (s/enum "invoke" "validate") "mode" :default "invoke")
        extra-keys-schema (s/find-extra-keys-schema (get-in schema [:request :header-params]))]
    (update-in schema [:request :header-params] merge {key value} (if-not extra-keys-schema {s/Any s/Any}))))

(defn- is-validate-request? [request]
  (= (get-in request [:headers mode-parameter]) "validate"))

(defn- attach-ring-meta [options handler]
  (let [type-config (get (:types options) (:type handler))
        input-schema (-> (:input handler)
                         (ring-input-schema (:parameters type-config))
                         attach-mode-parameter)]
    (assoc handler :ring {:type-config type-config
                          :uri (handler-uri handler)
                          :input input-schema})))

;;
;; Ring-handler
;;

; TODO: create a Ring-dispatcher
(s/defn ring-handler
  "Creates a ring handler from Dispatcher and options."
  ([dispatcher :- k/Dispatcher]
    (ring-handler dispatcher {}))
  ([dispatcher :- k/Dispatcher, options :- k/KeywordMap]
    (let [options (kc/deep-merge +default-options+ options)
          dispatcher (k/transform-handlers dispatcher (partial attach-ring-meta options))
          router (p/for-map [handler (k/all-handlers dispatcher nil)] (-> handler :ring :uri) handler)]
      (fn [{:keys [request-method uri] :as request}]
        (if-let [handler (router uri)]
          (if-let [type-config (-> handler :ring :type-config)]
            (if (get (:methods type-config) request-method)
              (let [action (:action handler)
                    ;; TODO: create an interceptor chain, validate params later to avoid detail leaking
                    ;; TODO: example: in example.cqrs, calling http POST :3000/api/item/reset-items!
                    ;; TODO: will reveal a) the expoint exists b) the input data format. Not ok'ish.
                    request (lazy-coerce-request! request handler options)
                    context (as-> {:request request} context

                                  ;; map parameters from ring-request into common keys
                                  (reduce kc/lazy-copy context (:parameters type-config))

                                  ;; global transformers first
                                  (reduce (fn [ctx mapper] (mapper ctx)) context (:transformers options))

                                  ;; type-level transformers
                                  (reduce (fn [ctx mapper] (mapper ctx)) context (:transformers type-config)))]

                (if (is-validate-request? request)
                  {:status 200, :headers {}, :body (k/validate dispatcher action context)}
                  (let [response (k/invoke dispatcher action context)]
                    (coerce!-response response handler options)))))))))))

;;
;; Routing
;;

(s/defn routes :- k/Function
  "Creates a ring handler of multiples handlers, matches in orcer."
  [ring-handlers :- [k/Function]]
  (apply some-fn ring-handlers))

(s/defn match
  "Creates a ring-handler for given uri & request-method"
  ([match-uri ring-handler]
    (match match-uri identity ring-handler))
  ([match-uri match-request-method ring-handler]
    (fn [{:keys [uri request-method] :as request}]
      (if (and (= match-uri uri)
               (match-request-method request-method))
        (ring-handler request)))))
