(ns kekkonen.swagger
  (:require [schema.core :as s]
            [ring.swagger.swagger2 :as rs2]
            [ring.util.http-response :refer [ok]]
            [ring.swagger.ui :as ui]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [plumbing.core :as p]
            [kekkonen.ring :as r]
            [clojure.string :as str]))

(s/defschema Options
  {(s/optional-key :ui) (s/maybe s/Str)
   (s/optional-key :spec) (s/maybe s/Str)
   (s/optional-key :data) k/KeywordMap
   (s/optional-key :options) {(s/optional-key :ui) k/KeywordMap
                              (s/optional-key :spec) k/KeywordMap}})

(defn transform-handler
  "Transforms a handler into ring-swagger path->method->operation map."
  [handler]
  (let [{:keys [description ns ring] {:keys [summary responses no-doc]} :meta} handler
        {:keys [parameters input methods uri]} ring
        ;; deep-merge back the mappings to get right request requirements
        input (reduce kc/deep-merge-from-to input parameters)
        {:keys [body-params query-params path-params header-params]} (:request input)]

    ;; discard handlers with :no-doc or without :ring metadata
    (if (and (not no-doc) ring)
      {uri (p/for-map [method (sort methods)]
             method (merge
                      (if ns {:tags [ns]})
                      (if description {:description description
                                       :summary description})
                      (if summary {:summary summary})
                      (if responses {:responses responses})
                      {:parameters (kc/strip-nil-values
                                     {:body body-params
                                      :query query-params
                                      :path path-params
                                      :header header-params})}))})))

(s/defn ring-swagger :- rs2/Swagger
  "Creates a ring-swagger object out of handlers and extra info"
  [handlers info]
  (merge info {:paths (apply merge (map transform-handler handlers))}))

(s/defn swagger-object
  "Creates a Swagger-spec object out of ring-swagger object and ring-swagger options."
  [ring-swagger :- rs2/Swagger, options :- k/KeywordMap]
  (rs2/swagger-json ring-swagger options))

(s/defn swagger-ui
  "Ring handler for the Swagger UI"
  [{:keys [ui spec] :as options}]
  (when ui
    (apply ui/swagger-ui (into [ui] (apply concat (merge
                                                    {:swagger-docs spec}
                                                    (-> options :options :ui)))))))

(defn- add-base-path
  "Extracts the base path from the context and adds it to the swagger map as basePath"
  [{:keys [context]} swagger]
  (if context
    (assoc swagger :basePath context)
    swagger))

(defn swagger-handler
  "Creates a handler, that serves the swagger-spec"
  [swagger options]
  (if-let [spec (:spec options)]
    (k/handler
      {:type :kekkonen.ring/handler
       :kekkonen.ring/method :get
       :name spec
       :no-doc true}
      (fn [{:keys [request] :as context}]
        (let [dispatcher (k/get-dispatcher context)
              ns (some-> context :request :query-params :ns str keyword)
              handlers (k/available-handlers dispatcher ns (#'r/clean-context context))]
          (ok (swagger-object
                (add-base-path request (ring-swagger handlers swagger))
                (-> options :options :spec))))))))
