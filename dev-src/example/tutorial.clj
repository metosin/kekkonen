(ns example.tutorial)

(require '[kekkonen.core :as k])
(require '[schema.core :as s])
(require '[plumbing.core :as p])

;;
;; Handlers
;;

; simplest thing that works
(def hello-world
  (k/handler
    {:name "hello-world"}
    (fn [_]
      "hello world.")))

(hello-world {})

; with more stuff
(def echo
  (k/handler
    {:description "this is a handler for echoing data"
     :name :echo
     :summery "echoes data"}
    (fn [{:keys [data]}]
      data)))

(echo {:data {:name "tommi"}})

; fnks
(def ^:handler plus
  (k/handler
    {:description "fnk echo"
     :name :fnkecho
     :summery "echoes data"}
    (p/fnk [[:data x :- s/Int, y :- s/Int]]
      (+ x y))))

(plus {:data {:x 1, :y 2}})

; defnk
(p/defnk ^:handler multiply
  "multiply x with y"
  {:responses {200 {:schema {:result s/Int}}}}
  [[:data x :- s/Int, y :- s/Int]]
  {:result (* x y)})

(multiply {:data {:x 4, :y 7}})

; stateful inc!
(p/defnk ^:handler inc!
  "adds a global counter"
  [[:components counter]]
  (swap! counter inc))

(inc! {:components {:counter (atom 10)}})

;;
;; Dispatcher
;;

; create
(def k (k/dispatcher
         {:handlers {:api {:calculator [#'multiply #'plus]
                           :stateful #'inc!
                           :others [echo
                                    hello-world]
                           :public (k/handler
                                     {:name :ping}
                                     (p/fnk []
                                       :pong))}}
          :context {:components {:counter (atom 0)}}}))

; get a handler
(k/some-handler k :api/nill)
(k/some-handler k :api/stateful/inc!)

; invoke a handler
(k/invoke k :api/stateful/inc!)

; can i call it?
(k/validate k :api/stateful/inc!)

; multi-tenant SAAS ftw?
(k/invoke k :api/stateful/inc! {:components {:counter (atom 100)}})
