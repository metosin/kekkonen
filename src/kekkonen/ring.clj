(ns kekkonen.ring
  (:require [schema.core :as s]
            [schema.coerce :as sc]
            [schema.utils :as su]
            [ring.swagger.coerce :as rsc]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]))

;;
;; options
;;

(def default-options
  {:types {:handler {:methods #{:post}}}
   :coercion {:query-params rsc/query-schema-coercion-matcher
              :path-params rsc/query-schema-coercion-matcher
              :form-params rsc/query-schema-coercion-matcher
              :header-params rsc/query-schema-coercion-matcher
              :body-params rsc/json-schema-coercion-matcher}})

(def http-types {:get {:methods #{:get}}
                 :head {:methods #{:head}}
                 :patch {:methods #{:patch}}
                 :delete {:methods #{:delete}}
                 :options {:methods #{:options}}
                 :post {:methods #{:post}}
                 :put {:methods #{:put}}
                 :any {:methods #{:get :head :patch :delete :options :post :put}}})

(def http-type-resolver (k/type-resolver :get :head :patch :delete :options :post :put :any))

;;
;; beef
;;

(s/defn uri->action :- s/Keyword
  "Converts an action keyword from a uri string."
  [path :- s/Str]
  (-> path (subs 1) keyword))

(defn coerce
  "Coerces a request against a handler input schema based on :coercion options."
  [request handler {:keys [coercion]}]
  (reduce
    (fn [request [k matcher]]
      (if-let [schema (get-in handler [:input :request k])]
        (let [value (get request k {})
              coercer (sc/coercer schema matcher)
              coerced (coercer value)]
          (if-not (su/error? coerced)
            (assoc request k coerced)
            (throw (ex-info "Coercion error" {:in k, :value value, :schema schema, :error coerced}))))
        request))
    request
    coercion))

(s/defn ring-handler
  "Creates a ring handler from Kekkonen and options."
  ([kekkonen]
    (ring-handler kekkonen {}))
  ([kekkonen options]
    (let [options (kc/deep-merge default-options options)]
      (fn [{:keys [request-method uri] :as request}]
        (let [action (uri->action uri)]
          (if-let [handler (k/some-handler kekkonen action)]
            (if-let [type-config (get (:types options) (:type handler))]
              (if (get (:methods type-config) request-method)
                (let [request (coerce request handler options)]
                  (k/invoke kekkonen action {:request request}))))))))))
