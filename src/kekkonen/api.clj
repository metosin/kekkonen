(ns kekkonen.api
  (:require [kekkonen.ring :as r]
            [kekkonen.core :as k]
            [kekkonen.middleware :as mw]
            [ring.util.http-response :refer [ok]]
            [kekkonen.swagger :as ks]
            [schema.core :as s]
            [kekkonen.common :as kc]))

(s/defschema Options
  {:core k/KeywordMap
   (s/optional-key :ring) k/KeywordMap
   (s/optional-key :mw) k/KeywordMap
   (s/optional-key :info) k/KeywordMap
   (s/optional-key :swagger) k/KeywordMap
   (s/optional-key :swagger-ui) k/KeywordMap})

(s/def +default-options+ :- Options
  {:core k/+default-options+
   :ring r/+default-options+
   :mw mw/+default-options+
   :info {}
   :swagger {}
   :swagger-ui ks/+default-swagger-ui-options+})

(s/defn api [options :- Options]
  (s/with-fn-validation
    (let [options (kc/deep-merge +default-options+ options)
          info (merge (:info options) (mw/api-info (:mw options)))
          registry (-> (k/create (:core options))
                       (k/inject (ks/swagger-handler info options)))]
      (mw/api-middleware
        (r/routes
          [(r/ring-handler registry (:ring options))
           (ks/swagger-ui (:swagger-ui options))])
        (:mw options)))))
