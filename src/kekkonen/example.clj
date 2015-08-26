(ns kekkonen.example
  (:require [schema.core :as s]
            [kekkonen.core :as k]
            [plumbing.core :as p]))

(p/defnk ^:command increment!
  "increments a counter"
  [[:resources counter]]
  (swap! counter inc))

(p/defnk ^:query say-hello
  "Says hello"
  [[:data name :- s/Str]]
  (str "hello " name))

(comment
  (def counter (atom 0))

  (increment! {:resources {:counter counter}})
  (say-hello {:data {:name "Tommi"}})

  (clojure.pprint/pprint (k/collect-ns 'kekkonen.example)))
