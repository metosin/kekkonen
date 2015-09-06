(ns kekkonen.midje
  (:require [midje.util.exceptions :as e]))

(defn throws?
  ([]
   (throws? {}))
  ([m]
   (fn [x]
     (let [data (ex-data (e/throwable x))
           mdata (if data (select-keys data (vec (keys m))))]
       (= mdata m)))))
