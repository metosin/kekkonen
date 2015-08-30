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

(fact "using services directly"

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
    (let [handlers (k/collect-ns 'kekkonen.core-test)]
      (count handlers) => 5

      (last handlers) => (just {:fn fn?
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
                                                   :name 'echo})})

      (fact "collect-ns-map"
        (k/collect-ns-map {:test1 'kekkonen.core-test
                           :test2 'kekkonen.core-test}) => {:test1 handlers, :test2 handlers}))))

(fact "kekkonen"
  (s/with-fn-validation

    (fact "can't be created without modules"
      (k/create {}) => (throws RuntimeException))

    (fact "can be created with modules"
      (let [kekkonen (k/create {:inject {:components {:db (atom #{})}}
                                :modules (k/collect-ns-map {:test 'kekkonen.core-test})})]

        (fact "all handlers"
          (count (k/all-handlers kekkonen)) => 5)

        (fact "non-existing action"
          (k/some-handler kekkonen :test/non-existing) => nil
          (k/invoke kekkonen :test/non-existing) => (throws RuntimeException))

        (fact "existing action contains :type and :module"
          (k/some-handler kekkonen :test/ping) => (contains {:type :function, :module :test})
          (k/invoke kekkonen :test/ping) => "pong")

        (fact "crud via kekkonen"
          (k/invoke kekkonen :test/get-items) => #{}
          (k/invoke kekkonen :test/add-item! {:data {:item "kikka"}}) => #{"kikka"}
          (k/invoke kekkonen :test/get-items) => #{"kikka"}
          (k/invoke kekkonen :test/reset-items!) => #{}
          (k/invoke kekkonen :test/get-items) => #{}

          (fact "context-level overrides FTW!"
            (k/invoke kekkonen :test/get-items {:components {:db (atom #{"hauki"})}}) => #{"hauki"}))))

    (fact "deeply nested modules"
      (let [kekkonen (k/create {:modules {:admin (k/collect-ns-map {:kikka 'kekkonen.core-test
                                                                    :kukka 'kekkonen.core-test})
                                          :public (k/collect-ns 'kekkonen.core-test)}})]
        (k/invoke kekkonen :admin/kikka/ping) => "pong"
        (k/invoke kekkonen :admin/kukka/ping) => "pong"
        (k/invoke kekkonen :public/ping) => "pong"))))
