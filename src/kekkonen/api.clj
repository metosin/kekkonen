(ns kekkonen.api
  (:require [kekkonen.ring :as r]
            [kekkonen.core :as k]
            [kekkonen.middleware :as mw]
            [kekkonen.swagger :as ks]
            [schema.core :as s]
            [kekkonen.common :as kc]))

(s/defschema Options
  {:core k/KeywordMap
   (s/optional-key :api) {:handlers k/KeywordMap}
   (s/optional-key :ring) r/Options
   (s/optional-key :mw) k/KeywordMap
   (s/optional-key :swagger) k/KeywordMap
   (s/optional-key :swagger-ui) k/KeywordMap})

(s/def +default-options+ :- Options
  {:core (-> k/+default-options+
             (assoc :coercion {:input nil, :output nil})
             (update :meta merge r/+ring-meta+))
   :api {:handlers r/+kekkonen-handlers+}
   :ring r/+default-options+
   :mw mw/+default-options+
   :swagger {:info {:title "Kekkonen API"}}
   :swagger-ui ks/+default-swagger-ui-options+})

(defn api [options]
  (s/with-fn-validation
    (let [options (s/validate Options (kc/deep-merge +default-options+ options))
          swagger (merge (:swagger options) (mw/api-info (:mw options)))
          dispatcher (-> (k/dispatcher (:core options))
                         (k/inject (-> options :api :handlers))
                         (k/inject (ks/swagger-handler swagger options)))]
      (mw/wrap-api
        (r/routes
          [(r/ring-handler dispatcher (:ring options))
           (ks/swagger-ui (:swagger-ui options))])
        (:mw options)))))
