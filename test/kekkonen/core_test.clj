(ns kekkonen.core-test
  (:require [kekkonen.core :as k]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [plumbing.core :as p]
            [clojure.set :as set]))

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

;;
;; facts
;;

(facts "coerce!"
  (let [schema {:data {:x s/Int, :y s/Int}}
        matcher (constantly nil)]

    (k/coerce! schema matcher {:data {:x 1, :y 2}} ..in.. ..type..)
    => {:data {:x 1, :y 2}}

    (k/coerce! schema matcher {:data {:x "1", :y "2"}} ..in.. ..type..)
    => (throws?
         {:in ..in..
          :type ..type..
          :value {:data {:x "1", :y "2"}}
          :schema schema})))

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

    (fact "anonymous functions"

      (fact "fn"

        (fact "with default type"
          (k/collect
            (k/handler
              {:name :echo
               :input {:name s/Str}}
              identity)
            k/default-type-resolver) => (just
                                          {(k/namespace {:name :echo})
                                           (just
                                             {:function fn?
                                              :description ""
                                              :user {}
                                              :type :handler
                                              :name :echo
                                              :input {:name s/Str}
                                              :output s/Any})}))

        (fact "type can be overridden"
          (k/collect
            (k/handler
              {:name :echo
               :type :kikka}
              identity)
            k/any-type-resolver) => (just
                                      {(k/namespace {:name :echo})
                                       (contains
                                         {:type :kikka})})))

      (fact "fnk"
        (k/collect
          (k/handler
            {:name :echo
             :description "Echoes the user"
             :query true
             :roles #{:admin :user}}
            (p/fnk f :- User [data :- User] data))
          k/default-type-resolver) => (just
                                        {(k/namespace {:name :echo})
                                         (just
                                           {:function fn?
                                            :type :handler
                                            :name :echo
                                            :user {:query true
                                                   :roles #{:admin :user}}
                                            :description "Echoes the user"
                                            :input {:data User
                                                    s/Keyword s/Any}
                                            :output User})}))

      (fact "with unresolved type"
        (let [handler (k/handler
                        {:name :echo
                         :input {:name s/Str}}
                        identity)]
          (k/collect
            handler
            (k/type-resolver :ILLEGAL)) => (throws? {:target handler}))))

    (fact "Var"
      (fact "with resolved type"
        (k/collect
          #'echo
          k/default-type-resolver) => (just
                                        {(k/namespace {:name :echo})
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
                                                          {:line irrelevant
                                                           :column irrelevant
                                                           :file string?
                                                           :ns 'kekkonen.core-test
                                                           :name 'echo})})}))

      (fact "with unresolved type"
        (k/collect #'echo (k/type-resolver :ILLEGAL)) => (throws? {:target #'echo})))

    (fact "Namespaces"
      (let [handlers (k/collect 'kekkonen.core-test k/default-type-resolver)]

        (count handlers) => 6
        handlers => (just
                      {(k/namespace {:name :ping}) k/handler?
                       (k/namespace {:name :get-items}) k/handler?
                       (k/namespace {:name :add-item!}) k/handler?
                       (k/namespace {:name :reset-items!}) k/handler?
                       (k/namespace {:name :echo}) k/handler?
                       (k/namespace {:name :plus}) k/handler?})))))

(fact "dispatcher"
  (s/with-fn-validation

    (fact "can't be created without handlers"
      (k/dispatcher {}) => throws?)

    (fact "can't be created with root level handlers"
      (k/dispatcher {:handlers 'kekkonen.core-test}) => throws?)

    (fact "can be created with namespaced handlers"
      (k/dispatcher {:handlers {:test 'kekkonen.core-test}}) => truthy)

    (fact "with handlers and context"
      (let [d (k/dispatcher {:context {:components {:db (atom #{})}}
                             :handlers {:test 'kekkonen.core-test}})]

        (fact "all handlers"
          (count (k/all-handlers d)) => 6)

        (fact "non-existing action"

          (fact "is nil"
            (k/some-handler d :test/non-existing) => nil)

          (fact "can't be validated (against a context)"
            (k/validate d :test/non-existing) => throws?)

          (fact "can't be invoked (against a context)"
            (k/invoke d :test/non-existing) => throws?))

        (facts "existing action"

          (fact "contains :type, :ns and :action"
            (k/some-handler d :test/ping) => (contains
                                               {:ns :test
                                                :type :handler
                                                :action :test/ping}))

          (fact "can be validated (against a context)"
            (k/validate d :test/ping) => nil)

          (fact "can be invoked (against a context)"
            (k/invoke d :test/ping) => "pong"))

        (fact "crud via dispatcher"
          (k/invoke d :test/get-items) => #{}
          (k/invoke d :test/add-item! {:data {:item "kikka"}}) => #{"kikka"}
          (k/invoke d :test/get-items) => #{"kikka"}
          (k/invoke d :test/reset-items!) => #{}
          (k/invoke d :test/get-items) => #{}

          (fact "context-level overrides FTW!"
            (k/invoke d :test/get-items {:components {:db (atom #{"hauki"})}}) => #{"hauki"}))))

    (fact "lots of handlers"
      (let [d (k/dispatcher {:handlers
                             {:admin
                              {:kikka 'kekkonen.core-test
                               :kukka 'kekkonen.core-test}
                              :public 'kekkonen.core-test
                              :kiss #'ping
                              :abba [#'ping #'echo]
                              :wasp ['kekkonen.core-test]
                              :bon (k/handler
                                     {:name :jovi}
                                     (constantly :runaway))}})]

        (fact "deeply nested"
          (fact "namespaces can be joined with ."
            (k/some-handler d :admin.kikka/ping) => (contains {:action :admin.kikka/ping})
            (k/invoke d :admin.kikka/ping) => "pong")
          (fact "ns is set to handler with ."
            (k/some-handler d :admin.kukka/ping) => (contains {:ns :admin.kukka})))

        (fact "not nested"
          (k/invoke d :kiss/ping) => "pong")

        (fact "var"
          (k/invoke d :abba/ping) => "pong")

        (fact "vector of vars"
          (k/invoke d :wasp/ping) => "pong")

        (fact "vector of namespaces"
          (k/invoke d :wasp/ping) => "pong")

        (fact "handler"
          (k/invoke d :bon/jovi) => :runaway)))

    (fact "sub-context"
      (let [d (k/dispatcher {:handlers {:api #'plus}})]

        (k/validate d :api/plus {}) => throws?
        (k/invoke d :api/plus {}) => throws?

        (k/validate d :api/plus {:data {:x 1}}) => throws?
        (k/invoke d :api/plus {:data {:x 1}}) => throws?

        (k/validate d :api/plus {:data {:x 1, :y 2}}) => nil
        (k/invoke d :api/plus {:data {:x 1, :y 2}}) => 3

        (let [k (k/with-context d {:data {:x 1}})]

          (k/validate k :api/plus {}) => throws?
          (k/invoke k :api/plus {}) => throws?

          (k/validate k :api/plus {:data {:x 1}}) => throws?
          (k/invoke k :api/plus {:data {:x 1}}) => throws?

          (k/validate k :api/plus {:data {:y 2}}) => nil
          (k/invoke k :api/plus {:data {:y 2}}) => 3)))))

(fact "special keys in context"
  (let [d (k/dispatcher {:handlers {:api (k/handler
                                           {:name :echo}
                                           identity)}})]
    (k/invoke d :api/echo) => (contains
                                {::k/dispatcher d
                                 ::k/handler (k/some-handler d :api/echo)})))

(defn defn-plus
  "defn-plus description"
  {:type ::input-output-schemas
   :input {:data {:x s/Int, :y s/Int}}
   :output {:result s/Int}}
  [{{:keys [x y]} :data}]
  {:result (+ x y)})

(p/defnk defnk-plus :- {:result s/Int}
  "defnk-plus description"
  {:type ::input-output-schemas}
  [[:data x :- s/Int, y :- s/Int]]
  {:result (+ x y)})

(facts "handler input & output schemas"
  (let [d (k/dispatcher {:handlers {:vars [#'defn-plus #'defnk-plus]
                                    :fns [(k/handler
                                            {:name :fn-plus
                                             :type ::input-output-schemas
                                             :input {:data {:x s/Int, :y s/Int}}
                                             :output {:result s/Int}}
                                            (fn [{{:keys [x y]} :data}]
                                              {:result (+ x y)}))
                                          (k/handler
                                            {:name :fnk-plus
                                             :type ::input-output-schemas}
                                            (p/fnk f :- {:result s/Int} [[:data x :- s/Int, y :- s/Int]]
                                              {:result (+ x y)}))]}
                         :type-resolver (k/type-resolver ::input-output-schemas)})]

    (fact "handlers are registered ok"
      (k/invoke d :vars/defn-plus {:data {:x 1 :y 2}}) => {:result 3}
      (k/invoke d :vars/defnk-plus {:data {:x 1 :y 2}}) => {:result 3}
      (k/invoke d :fns/fn-plus {:data {:x 1 :y 2}}) => {:result 3}
      (k/invoke d :fns/fnk-plus {:data {:x 1 :y 2}}) => {:result 3})

    (fact "handler input & output schemas are ok"
      (k/some-handler d :vars/defn-plus) => (contains
                                              {:description "defn-plus description"
                                               :input {:data {:x s/Int, :y s/Int}}
                                               :output {:result s/Int}})
      (k/some-handler d :vars/defnk-plus) => (contains
                                               {:description "defnk-plus description"
                                                :input {:data {:x s/Int, :y s/Int, s/Keyword s/Any}, s/Keyword s/Any}
                                                :output {:result s/Int}})
      (k/some-handler d :fns/fn-plus) => (contains
                                           {:input {:data {:x s/Int, :y s/Int}}
                                            :output {:result s/Int}})
      (k/some-handler d :fns/fnk-plus) => (contains
                                            {:input {:data {:x s/Int, :y s/Int, s/Keyword s/Any}, s/Keyword s/Any}
                                             :output {:result s/Int}}))))

(defn role-enforcer [context required-roles]
  (let [roles (::roles context)]
    (if (seq (set/intersection roles required-roles))
      context
      (throw (ex-info
               "invalid role"
               {:roles roles
                :required required-roles})))))

(facts "user-meta"
  (fact "on handler"
    (let [d (k/dispatcher
              {:handlers {:api (k/handler
                                 {:name :test
                                  ::roles #{:admin}}
                                 (p/fn-> :x))}
               :user {::roles role-enforcer}})]

      (fact "user-meta is populated correctly"
        (k/all-handlers d) => (just [(contains {:user {::roles #{:admin}}
                                                :ns-user []
                                                :all-user [{::roles #{:admin}}]})]))

      (fact "invoking api enforces rules"

        (k/invoke d :api/test {:x 1})
        => (throws? {:roles nil, :required #{:admin}})

        (k/invoke d :api/test {:x 1 ::roles #{:user}})
        => (throws? {:roles #{:user}, :required #{:admin}})

        (k/invoke d :api/test {:x 1 ::roles #{:admin}})
        => 1)))

  (fact "nested rules on namespaces"
    (let [api-ns (k/namespace {:name :api, ::roles #{:anyone}})
          admin-ns (k/namespace {:name :admin, ::roles #{:admin}})
          handler (k/handler {:name :test, ::roles #{:superadmin}} (p/fn-> :x))
          d (k/dispatcher
              {:handlers {api-ns {admin-ns handler}}
               :user {::roles role-enforcer}})]

      (fact "user-meta is populated correctly"
        (k/all-handlers d) => (just [(contains {:user {::roles #{:superadmin}}
                                                :ns-user [{::roles #{:anyone}}
                                                          {::roles #{:admin}}]
                                                :all-user [{::roles #{:anyone}}
                                                           {::roles #{:admin}}
                                                           {::roles #{:superadmin}}]})]))

      (fact "invoking api enforces rules"

        (k/invoke d :api.admin/test {:x 1})
        => (throws? {:roles nil, :required #{:anyone}})

        (k/invoke d :api.admin/test {:x 1 ::roles #{:anyone}})
        => (throws? {:roles #{:anyone}, :required #{:admin}})

        (k/invoke d :api.admin/test {:x 1 ::roles #{:anyone, :admin}})
        => (throws? {:roles #{:anyone :admin}, :required #{:superadmin}})

        (k/invoke d :api.admin/test {:x 1 ::roles #{:anyone, :admin, :superadmin}})
        => 1))))

(facts "mass availability & validation"
  (let [admin-ns (k/namespace {:name :admin, ::roles #{:admin}})
        handler1 (k/handler {:name :handler1} (p/fnk [] true))
        handler2 (k/handler {:name :handler2} (p/fnk [[:data x :- s/Bool]] x))
        d (k/dispatcher {:user {::roles role-enforcer}
                         :handlers {:api {admin-ns [handler1 handler2]
                                          :public [handler1 handler2]}}})]

    (fact "4 handlers exist"
      (k/all-handlers d) => (n-of k/handler? 4))

    (fact "4 handlers exist under :api"
      (k/all-handlers d :api) => (n-of k/handler? 4))

    (fact "2 handlers exist under :api.admin"
      (k/all-handlers d :api.admin) => (n-of k/handler? 2))

    (fact "allow only exact matches on the namespace"
      (k/all-handlers d :api.adm) => nil)

    (fact "only 2 are available & validated"
      (k/available-handlers d {}) => (n-of k/handler? 2)
      (k/validated-handlers d {}) => (n-of k/handler? 2))

    (fact "0 are available & validated under :api.admin"
      (k/available-handlers d {} :api.admin) => (n-of k/handler? 0)
      (k/validated-handlers d {} :api.admin) => (n-of k/handler? 0))

    (fact "4 are available & validated when all the rules apply"
      ;(k/available-handlers d {::roles #{:admin}}) => (n-of k/handler? 4)
      (k/validated-handlers d {::roles #{:admin}}) => (n-of k/handler? 4)

      (fact "2 are available under :api.admin when all the rules apply"
        (k/available-handlers d {::roles #{:admin}} :api.admin) => (n-of k/handler? 2))

      (fact "1 is validated"
        (k/validated-handlers d {::roles #{:admin}} :api.admin) => (n-of k/handler? 1))

      (fact "interacting with a spesific handler"
        (fact "with missing parameters"
          (let [ctx {::roles #{:admin}}]
            (k/check d :api.admin/handler2 ctx) => nil
            (k/validate d :api.admin/handler2 ctx) => throws?
            (k/invoke d :api.admin/handler2 ctx) => throws?

            (fact "with all parameters"
              (let [ctx (merge ctx {:data {:x true}})]
                (k/check d :api.admin/handler2 ctx) => nil
                (k/validate d :api.admin/handler2 ctx) => throws?
                (k/invoke d :api.admin/handler2 ctx) => throws?))))))))

(fact "context transformations"
  (let [copy-ab-to-cd (k/context-copy [:a :b] [:c :d])
        remove-ab (k/context-dissoc [:a :b])]

    (copy-ab-to-cd {:a {:b 1}}) => {:a {:b 1} :c {:d 1}}
    (remove-ab {:a {:b 1}}) => {}
    ((comp remove-ab copy-ab-to-cd) {:a {:b 1}}) => {:c {:d 1}}))

(fact "transforming"
  (s/with-fn-validation
    (let [d (k/dispatcher {:handlers {:api (k/handler {:name :test} #(:y %))}
                           :transformers [(k/context-copy [:x] [:y])
                                          (k/context-dissoc [:x])]})]

      (fact "transformers are executed"
        (k/invoke d :api/test {:x 1}) => 1))))

(fact "transforming handlers"
  (fact "enriching handlers"
    (k/transform-handlers
      (k/dispatcher {:handlers {:api (k/handler {:name :test} identity)}})
      (fn [handler]
        (assoc handler :kikka :kukka)))

    => (contains
         {:handlers
          (just
            {:api/test
             (contains
               {:kikka :kukka})})}))

  (fact "stripping handlers"
    (k/transform-handlers
      (k/dispatcher {:handlers {:api (k/handler {:name :test} identity)}})
      (constantly nil))

    => (contains
         {:handlers {}})))

(fact "invoke-time extra data"
  (let [d (k/dispatcher {:handlers
                         {:api
                          [(k/handler
                             {:name :dispatcher}
                             (partial k/get-dispatcher))
                           (k/handler
                             {:name :handler
                              :description "metameta"}
                             (partial k/get-handler))
                           (k/handler
                             {:name :names}
                             (fn [context]
                               (->> context
                                    k/get-dispatcher
                                    k/all-handlers
                                    (map :name)
                                    set)))]}})]

    (fact "::dispatcher"
      (s/validate k/Dispatcher (k/invoke d :api/dispatcher)) => truthy)

    (fact "::handler"
      (k/invoke d :api/handler) => (contains {:description "metameta"}))

    (fact "going meta boing boing"
      (k/invoke d :api/names) => #{:dispatcher :handler :names})))

; TODO: will override paths as we do merge
(fact "handlers can be injected into existing dispatcher"
  (let [d (-> (k/dispatcher {:handlers {:api (k/handler {:name :test} identity)}})
              (k/inject (k/handler {:name :ping} identity)))]
    d => (contains
           {:handlers
            (just
              {:api/test anything
               :ping anything})})))

(facts "coercion-matcher"
  (let [PositiveInt (s/both s/Int (s/pred pos? 'positive))
        handlers {:api [(k/handler
                          {:name :plus}
                          (p/fnk plus :- {:result PositiveInt} [[:data x :- s/Int, y :- PositiveInt]]
                            {:result (+ x y)}))]}]

    (facts "with default settings"
      (let [d (k/dispatcher {:handlers handlers})]

        (fact "input is validated"

          (k/invoke d :api/plus {:data {:x 1, :y 1}})
          => {:result 2}

          (k/invoke d :api/plus {:data {:x 1, :y -10}})
          => (throws?
               {:type :kekkonen.core/request
                :in nil
                :value {:data {:x 1, :y -10}}
                :schema {:data {:x s/Int, :y PositiveInt, s/Keyword s/Any}, s/Keyword s/Any}}))

        (fact "return is validated"

          (k/invoke d :api/plus {:data {:x -10, :y 1}})
          => (throws?
               {:type :kekkonen.core/response
                :in nil
                :value {:result -9}
                :schema {:result PositiveInt}})))

      (facts "with input coercion turned off"
        (let [d (k/dispatcher {:handlers handlers
                               :coercion {:input nil}})]

          (fact "input is not validated"
            (k/invoke d :api/plus {:data {:x 1, :y 1}})
            => {:result 2}

            (k/invoke d :api/plus {:data {:x 20, :y -10}})
            => {:result 10})))

      (facts "with input & output coercion turned off"
        (let [d (k/dispatcher {:handlers handlers
                               :coercion {:input nil
                                          :output nil}})]

          (fact "input is not validated"
            (k/invoke d :api/plus {:data {:x 1, :y 1}})
            => {:result 2}

            (k/invoke d :api/plus {:data {:x 0, :y -10}})
            => {:result -10}))))))
