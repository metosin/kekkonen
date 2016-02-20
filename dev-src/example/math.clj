(ns example.math
  (:require [plumbing.core :refer [defnk]]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]))

(defnk ^:query ping []
  (success {:ping "pong"}))

(defnk ^:query plus
  [[:data x :- s/Int, y :- s/Int]]
  (success (+ x y)))

(defnk ^:command increment
  [counter]
  (success (swap! counter inc)))
