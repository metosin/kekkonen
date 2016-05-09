(ns sample.handler
  (:require [plumbing.core :as p]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]))

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :S :M :L)
   :origin {:country (s/enum :FI :PO)}})

;;
;; Handlers
;;

(p/defnk ^:query ping []
  (success {:ping "pong"}))

(p/defnk ^:command echo-pizza
  "Echoes a pizza"
  {:responses {:default {:schema Pizza}}}
  [data :- Pizza]
  (success data))

(p/defnk ^:query plus
  "playing with data"
  [[:data x :- s/Int, y :- s/Int]]
  (success (+ x y)))

(p/defnk ^:command inc!
  "a stateful counter"
  [counter]
  (success (swap! counter inc)))

;;
;; Application
;;

(p/defnk create [[:state counter]]
  (cqrs-api
    {:swagger {:data {:info {:title "Kekkonen with Component"
                             :description "created with http://kekkonen.io"}}}
     :core {:handlers {:api {:pizza #'echo-pizza
                             :math [#'inc! #'plus]
                             :ping #'ping}}
            :context {:counter counter}}}))
