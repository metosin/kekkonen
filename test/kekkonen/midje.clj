(ns kekkonen.midje
  (:require [midje.util.exceptions :as e]
            [kekkonen.core :as k]
            [schema.core :as s]
            [cheshire.core :as c]))

(defn throws?
  ([]
   (throws? {}))
  ([m]
   (fn [x]
     (let [data (ex-data (e/throwable x))
           mdata (if data (select-keys data (vec (keys m))))]
       (and
         (not (nil? x))
         (every?
           (fn [[k v]]
             (let [v' (get mdata k)]
               (if (fn? v)
                 (v v')
                 (= v v'))))
           m))))))

(def schema-error? (throws? {:type ::s/error}))
(def missing-route? (throws? {:type ::k/dispatch}))
(def input-coercion-error? (throws? {:type ::k/request}))
(def output-coercion-error? (throws? {:type ::k/response}))

(defn parse [x]
  (if (and x (:body x))
    (c/parse-string (slurp (:body x)) true)))
