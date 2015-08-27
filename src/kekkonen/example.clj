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
  {:roles #{:admin}}
  [[:data name :- s/Str]]
  (str "hello " name))

(comment
  (def counter (atom 0))

  (increment! {:resources {:counter counter}})
  (say-hello {:data {:name "Tommi"}})

  (./aprint
    (k/collect {:collector k/defnk->handler
                :modules {:items 'kekkonen.example}}))

  (def kekkonen (k/create
                  (k/collect {:modules {:items 'kekkonen.example}})))

  (k/some-action kekkonen :items :increment!)
  (k/some-action kekkonen :items/increment!)

  (./aprint
    (k/create
      (k/collect {:modules {:items 'kekkonen.example}})))

  (./aprint (k/collect-ns 'kekkonen.example)))
