(ns kekkonen.perf
  (:require [criterium.core :as cc]
            [kekkonen.core :as k]
            [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]))

(p/defnk ^:handler plus1 :- {:body {:result s/Int}, s/Keyword s/Any}
  "adds numbers together"
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(defn ^:handler plus2
  "adds numbers together"
  {:input {:data {:x s/Int, :y s/Int}}
   :output {:body {:result s/Int}, s/Keyword s/Any}}
  [{{:keys [x y]} :data}]
  (success {:result (+ x y)}))

(def d1 (k/dispatcher {:handlers {:api [#'plus1 #'plus2]}}))
(def d2 (k/dispatcher {:handlers {:api [#'plus1 #'plus2]}
                       :coercion {:input nil, :output nil}}))

(defn title [s]
  (println (str "\u001B[35m" (apply str (repeat (count s) "#")) "\u001B[0m"))
  (println (str "\u001B[35m" s "\u001B[0m"))
  (println (str "\u001B[35m" (apply str (repeat (count s) "#")) "\u001B[0m")))

(defn bench []

  (title "with coercion")
  (cc/quick-bench (k/invoke d1 :api/plus1 {:data {:x 10, :y 20}}))
  ; 30.9µs µs (memoied)

  (title "without coercion")
  (cc/quick-bench (k/invoke d2 :api/plus1 {:data {:x 10, :y 20}}))
  ; 4.7µs -> µs (memoized)

  (println))

(comment
  (bench))

