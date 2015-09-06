(ns kekkonen.core-test
  (:require [kekkonen.core :as k]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [plumbing.core :as p]))

;;
;; test handlers
;;

(p/defnk ^:handler ping [] "pong")

(p/defnk ^:handler get-items :- #{s/Str}
  [[:components db]] @db)

(p/defnk ^:handler add-item! :- #{s/Str}
  "Adds an item to database"
  [[:data item :- String]
   [:components db]]
  (swap! db conj item))

(p/defnk ^:handler reset-items! :- #{s/Str}
  "Resets the database"
  [[:components db]]
  (swap! db empty))

(p/defnk ^:handler plus
  [[:data x :- s/Int, y :- s/Int]]
  (+ x y))

(s/defschema User
  {:name s/Str
   :address {:street s/Str
             :zip s/Int
             (s/optional-key :country) (s/enum :FI :CA)}})

(p/defnk ^:handler echo :- User
  "Echoes the user"
  {:roles #{:admin :user}}
  [data :- User]
  data)

(p/defnk ^:query not-a-handler [])

;;
;; facts
;;

(fact "using services directly"

  (fact "simple query works"
    (ping {}) => "pong")

  (fact "stateful services"
    (let [db (atom #{})]

      (fact "call fails with missing dependencies"
        (get-items {}) => throws?)

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
                      :data {:item 123}})) => throws?)

      (fact "call with right types succeeds with validation set"
        (s/with-fn-validation
          (add-item! {:components {:db db}
                      :data {:item "kikka"}})) => #{"kikka"}
        (get-items {:components {:db db}}) => #{"kikka"}))))

;;
;; Collecting services
;;

(fact "resolving types"
  (fact "successfull resolution"
    ((k/type-resolver :kikka) {:kikka true}) => {:type :kikka}
    ((k/type-resolver :kikka :kukka) {:kukka true}) => {:type :kukka})
  (fact "unsuccessfull resolution"
    ((k/type-resolver :kikka) {}) => nil
    ((k/type-resolver :kikka :kukka) {}) => nil))

(fact "collecting handlers"
  (s/with-fn-validation

    (fact "functions"

      (fact "with fnk"
        (let [handler (k/collect
                        (k/handler
                          {:name :echo
                           :description "Echoes the user"
                           :query true
                           :roles #{:admin :user}}
                          (p/fnk f :- User [data :- User] data)))]

          handler => (contains {:function fn?
                                :type :handler
                                :name :echo
                                :user {:query true
                                       :roles #{:admin :user}}
                                :description "Echoes the user"
                                :input {:data User
                                        s/Keyword s/Any}
                                :output User}))))

    (fact "vars"
      (let [handler (k/collect #'echo)]

        handler => (just
                     {:echo
                      (just
                        {:function fn?
                         :type :handler
                         :name :echo
                         :user {:roles #{:admin :user}}
                         :description "Echoes the user"
                         :input {:data User
                                 s/Keyword s/Any}
                         :output User
                         :source-map (just
                                       {:line 37
                                        :column 1
                                        :file string?
                                        :ns 'kekkonen.core-test
                                        :name 'echo})})})

        (fact "namespaces"
          (let [handlers (k/collect 'kekkonen.core-test)]

            (count handlers) => 6
            handlers => (just
                          {:ping k/handler?
                           :get-items k/handler?
                           :add-item! k/handler?
                           :reset-items! k/handler?
                           :echo k/handler?
                           :plus k/handler?})))))))

(fact "kekkonen"
  (s/with-fn-validation

    (fact "can't be created without handlers"
      (k/create {}) => throws?)

    (fact "can't be created with root level handlers"
      (k/create {:handlers 'kekkonen.core-test}) => throws?)

    (fact "can be created with namespaced handlers"
      (k/create {:handlers {:test 'kekkonen.core-test}}) => truthy)

    (fact "with handlers and context"
      (let [kekkonen (k/create {:context {:components {:db (atom #{})}}
                                :handlers {:test 'kekkonen.core-test}})]

        (fact "all handlers"
          (count (k/all-handlers kekkonen)) => 6)

        (fact "non-existing action"
          (k/some-handler kekkonen :test/non-existing) => nil
          (k/invoke kekkonen :test/non-existing) => throws?)

        (fact "existing action contains :type and :ns"
          (k/some-handler kekkonen :test/ping) => (contains {:type :handler, :ns :test})
          (k/invoke kekkonen :test/ping) => "pong")

        (fact "crud via kekkonen"
          (k/invoke kekkonen :test/get-items) => #{}
          (k/invoke kekkonen :test/add-item! {:data {:item "kikka"}}) => #{"kikka"}
          (k/invoke kekkonen :test/get-items) => #{"kikka"}
          (k/invoke kekkonen :test/reset-items!) => #{}
          (k/invoke kekkonen :test/get-items) => #{}

          (fact "context-level overrides FTW!"
            (k/invoke kekkonen :test/get-items {:components {:db (atom #{"hauki"})}}) => #{"hauki"}))))

    (fact "lots of handlers"
      (let [kekkonen (k/create {:handlers
                                {:admin
                                 {:kikka 'kekkonen.core-test
                                  :kukka 'kekkonen.core-test}
                                 :public 'kekkonen.core-test
                                 :kiss #'ping
                                 :abba [#'ping #'echo]
                                 :wasp ['kekkonen.core-test]}})]

        (fact "deeply nested"
          (k/invoke kekkonen :admin/kikka/ping) => "pong"
          (k/invoke kekkonen :admin/kukka/ping) => "pong")

        (fact "not nested"
          (k/invoke kekkonen :kiss/ping) => "pong")

        (fact "var"
          (k/invoke kekkonen :abba/ping) => "pong")

        (fact "vector of vars"
          (k/invoke kekkonen :wasp/ping) => "pong")

        (fact "vector of namespaces"
          (k/invoke kekkonen :wasp/ping) => "pong")))

    (fact "sub-context"
      (let [kekkonen (k/create {:handlers {:api #'plus}})]

        (k/invoke kekkonen :api/plus {}) => throws?
        (k/invoke kekkonen :api/plus {:data {:x 1}}) => throws?
        (k/invoke kekkonen :api/plus {:data {:x 1, :y 2}}) => 3

        (let [kekkonen (k/with-context kekkonen {:data {:x 1}})]
          (k/invoke kekkonen :api/plus {}) => throws?
          (k/invoke kekkonen :api/plus {:data {:x 1}}) => throws?
          (k/invoke kekkonen :api/plus {:data {:y 2}}) => 3)))))

(p/defnk ^:test meta-handler {::roles #{:admin}} [x] x)

(fact "user-meta"
  (let [k (k/create {:handlers {:api #'meta-handler}
                     :type-resolver (k/type-resolver :test)
                     :user {::roles (fn [context allowed-roles]
                                      (let [role (::role context)]
                                        (if (allowed-roles role)
                                          context
                                          (throw (ex-info "invalid role" {:role role
                                                                          :required allowed-roles})))))}})]

    (k/all-handlers k) => (just [anything])

    (k/invoke k :api/meta-handler {:x 1}) => (throws RuntimeException)
    (k/invoke k :api/meta-handler {:x 1 ::role :user}) => (throws RuntimeException)
    (k/invoke k :api/meta-handler {:x 1 ::role :admin}) => 1))

