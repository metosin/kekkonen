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

;;
;; Spike
;;

(comment
  (def counter (atom 0))

  (increment! {:resources {:counter counter}})
  (say-hello {:data {:name "Tommi"}})

  (./aprint
    (s/with-fn-validation
      (k/collect-ns-map {:items 'kekkonen.example})))

  (def kekkonen (k/create
                  {:inject {:resources {:counter counter}}
                   :modules (k/collect-ns-map {:items 'kekkonen.example})}))

  (./aprint kekkonen)

  (k/some-handler kekkonen :items/increment!)
  (k/invoke kekkonen :items/increment! {})
  (k/invoke kekkonen :items/increment!)
  (k/invoke kekkonen :items/increment! {:resources {:counter (atom 0)}})

  (./aprint
    (k/create
      {:modules (k/collect-ns-map {:items 'kekkonen.example})}))

  (./aprint (k/collect-ns k/defnk->handler 'kekkonen.example)))
