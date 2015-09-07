(ns kekkonen.api
  (:require [kekkonen.ring :as r]
            [kekkonen.core :as k]
            [kekkonen.middleware :as mw]
            [schema.core :as s]))

(s/defschema Options
  {:core k/KeywordMap
   (s/optional-key :ring) k/KeywordMap
   (s/optional-key :mw) k/KeywordMap})

(s/defn api [options :- Options]
  (s/with-fn-validation
    (mw/api-middleware
      (r/ring-handler
        (k/create
          (:core options))
        (:ring options))
      (:mw options))))
