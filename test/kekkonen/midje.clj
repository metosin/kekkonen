(ns kekkonen.midje
  (:require [midje.util.exceptions :as e]
            [cheshire.core :as c]))

(defn throws?
  ([]
   (throws? {}))
  ([m]
   (fn [x]
     (let [data (ex-data (e/throwable x))
           mdata (if data (select-keys data (vec (keys m))))]
       (= mdata m)))))

(defn parse [x]
  (if x
    (c/parse-string (slurp (:body x)) true)))
