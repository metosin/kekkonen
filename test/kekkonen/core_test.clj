(ns kekkonen.core-test
  (:require [kekkonen.core :as k]
            [midje.sweet :refer :all]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]))

; simplest thing that works
(defnk ping [] "pong")

; a simple query
(defnk ^:query get-items :- #{s/Str}
  [[:components db]] @db)

; a simple update
(defnk ^:command add-item! :- #{s/Str}
  "adds an item to database"
  [[:data item :- String]
   [:components db]]
  (swap! db conj item))

(defnk ^:command reset-items! :- #{s/Str}
  "resets the database"
  [[:components db]]
  (swap! db empty))

(fact "using services directrly"

  (fact "simple query works"
    (ping {}) => "pong")

  (fact "stateful services"
    (let [db (atom #{})]

      (fact "call fails with missing dependencies"
        (get-items {}) => (throws Exception))

      (fact "call with dependencies set succeeds"
        (get-items {:components {:db db}}) => #{})

      (fact "call with dependencies with extra data succeeds"
        (get-items {:components {:db db}
                    :EXTRA {:kikka :kukka}
                    :data {}}) => #{})

      (fact "call with wrong types succeeds without validation set"
        (add-item! {:components {:db db}
                    :data {:item 123}}) => #{123}
        (reset-items! {:components {:db db}}) => #{})

      (fact "call with wrong types succeeds without validation set"
        (s/with-fn-validation
          (add-item! {:components {:db db}
                      :data {:item 123}})) => (throws Exception))

      (fact "call with right types succeeds with validation set"
        (s/with-fn-validation
          (add-item! {:components {:db db}
                      :data {:item "kikka"}})) => #{"kikka"}
        (get-items {:components {:db db}}) => #{"kikka"}))))
