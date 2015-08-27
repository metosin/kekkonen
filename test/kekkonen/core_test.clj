(ns kekkonen.core-test
  (:require [kekkonen.core :as k]
            [midje.sweet :refer :all]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]))

; simplest thing that works
(defnk ping [] "pong")

; crud
(defnk get-items :- #{s/Str}
  [[:components db]] @db)

(defnk add-item! :- #{s/Str}
  "Adds an item to database"
  [[:data item :- String]
   [:components db]]
  (swap! db conj item))

(defnk reset-items! :- #{s/Str}
  "Resets the database"
  [[:components db]]
  (swap! db empty))

(s/defschema User
  {:name s/Str
   :address {:street s/Str
             :zip s/Int
             (s/optional-key :country) (s/enum :FI :CA)}})

; complex example with external schema and user meta
(defnk ^:query echo :- User
  "Echoes the user"
  {:roles #{:admin :user}}
  [data :- User]
  data)

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

;;
;; Collecting services
;;

(fact "collect-ns"
  (s/with-fn-validation
    (let [services (k/collect-ns k/defnk->handler 'kekkonen.core-test)]
      (count services) => 5

      (last services) => (just {:fn fn?
                                :name :echo
                                :user {:query true
                                       :roles #{:admin :user}}
                                :description "Echoes the user"
                                :input {:data User
                                        s/Keyword s/Any}
                                :output User
                                :source-map (just {:line 32
                                                   :column 1
                                                   :file string?
                                                   :ns 'kekkonen.core-test
                                                   :name 'echo})}))))
