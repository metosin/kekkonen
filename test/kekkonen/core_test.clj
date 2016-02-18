(ns kekkonen.core-test
  (:require [kekkonen.core :as k]
            [kekkonen.midje :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [plumbing.core :as p]
            [clojure.set :as set])
  (:import [kekkonen.core Dispatcher]))

(background
  (around :facts (s/with-fn-validation ?form)))

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
        (get-items {}) => (throws?))

      (fact "call with dependencies set succeeds"
        (get-items {:components {:db db}}) => #{})

      (fact "call with dependencies with extra data succeeds"
        (get-items {:components {:db db}
                    :EXTRA {:kikka :kukka}
                    :data {}}) => #{})

      (fact "call with wrong types succeeds without validation set"
        (s/without-fn-validation
          (add-item! {:components {:db db}
                      :data {:item 123}}) => #{123}
          (reset-items! {:components {:db db}}) => #{}))

      (fact "call with wrong types succeeds without validation set"
        (add-item! {:components {:db db}
                    :data {:item 123}}) => (throws?))

      (fact "call with right types succeeds with validation set"
        (add-item! {:components {:db db}
                    :data {:item "kikka"}}) => #{"kikka"}
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
                     (k/namespace {:name :plus}) k/handler?}))))

(fact "dispatcher"

  (fact "can't be created without handlers"
    (k/dispatcher {}) => (throws?))

  (fact "can't be created with root level handlers"
    (k/dispatcher {:handlers 'kekkonen.core-test}) => (throws?))

  (fact "can be created with namespaced handlers"
    (k/dispatcher {:handlers {:test 'kekkonen.core-test}}) => truthy)

  (fact "with handlers and context"
    (let [d (k/dispatcher {:context {:components {:db (atom #{})}}
                           :handlers {:test 'kekkonen.core-test}})]

      (fact "all handlers"
        (count (k/all-handlers d nil)) => 6)

      (fact "non-existing action"

        (fact "is nil"
          (k/some-handler d :test/non-existing) => nil)

        (fact "can't be validated (against a context)"
          (k/validate d :test/non-existing) => missing-route?)

        (fact "can't be invoked (against a context)"
          (k/invoke d :test/non-existing) => missing-route?))

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

      (k/validate d :api/plus {}) => input-coercion-error?
      (k/invoke d :api/plus {}) => input-coercion-error?

      (k/validate d :api/plus {:data {:x 1}}) => input-coercion-error?
      (k/invoke d :api/plus {:data {:x 1}}) => input-coercion-error?

      (k/validate d :api/plus {:data {:x 1, :y 2}}) => nil
      (k/invoke d :api/plus {:data {:x 1, :y 2}}) => 3

      (let [k (k/with-context d {:data {:x 1}})]

        (k/validate k :api/plus {}) => input-coercion-error?
        (k/invoke k :api/plus {}) => input-coercion-error?

        (k/validate k :api/plus {:data {:x 1}}) => input-coercion-error?
        (k/invoke k :api/plus {:data {:x 1}}) => input-coercion-error?

        (k/validate k :api/plus {:data {:y 2}}) => nil
        (k/invoke k :api/plus {:data {:y 2}}) => 3))))

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
                                            (p/fnk f :- {:result s/Int}
                                              [[:data x :- s/Int, y :- s/Int]]
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

(defn require-role [required]
  (fn [context]
    (let [roles (::roles context)]
      (if (seq (set/intersection roles required))
        context))))

(defn require-role! [required]
  (some-fn
    (require-role required)
    (fn [context]
      (throw (ex-info "missing role" {:roles (::roles context)
                                      :required required})))))

(fact "interceptor-factory"
  (let [{:keys [enter leave]} (#'k/interceptor-factory [{:enter #(update % :enter inc)
                                                         :leave #(update % :leave dec)}
                                                        (fn [ctx] (update ctx :enter inc))
                                                        {:enter #(update % :enter (partial * 10))
                                                         :leave #(update % :leave (partial * 10))}])]
    (fact "enters are applied in order, leaves in reverse order"
      (leave (enter {:enter 0, :leave 0})) => {:enter 20, :leave -1})))

(facts "user-meta"
  (let [inc* (fn [value]
               {:enter (p/fnk [[:data x :- s/Int] :as ctx]
                         (update-in ctx [:data :x] #(+ % value)))})
        intercept* (fn [[enter leave]]
                     {:enter (p/fnk [[:data x :- s/Int] :as ctx]
                               (update-in ctx [:data :x] enter))
                      :leave (fn [ctx]
                               (update ctx :response leave))})
        times* (fn [value]
                 (p/fnk [[:data x :- s/Int] :as ctx]
                   (update-in ctx [:data :x] #(* % value))))

        x10 (partial * 10)]

    (facts "context-handlers via map"
      (let [d (k/dispatcher
                {:handlers {:api (k/handler
                                   {:name :test
                                    ::inc 2
                                    ::intercept [dec x10]
                                    ::times 2}
                                   (p/fn-> :data :x))}
                 :user {::inc inc*
                        ::intercept intercept*
                        ::times times*}})]

        (fact "user-meta is populated correctly"
          (k/some-handler d :api/test) => (contains {:user {::inc 2, ::intercept [dec x10], ::times 2}
                                                     :ns-user []
                                                     :all-user [{::inc 2, ::intercept [dec x10], ::times 2}]}))

        (fact "are executed in some order: (-> 2 (+ 2) dec (* 2) (* 10) => 60"
          (k/invoke d :api/test {:data {:x 2}}) => 60)))

    (facts "context-handlers via vector of vectors"
      (fact "handler meta"
        (fact "are executed in order 1/2"
          (let [d (k/dispatcher
                    {:handlers {:api (k/handler
                                       {:name :test
                                        ::inc 1
                                        ::times 2}
                                       (p/fn-> :data :x))}
                     :user [[::inc inc*]
                            [::times times*]]})]

            (fact "user-meta is populated correctly"
              (k/some-handler d :api/test)
              => (contains {:user {::inc 1 ::times 2}
                            :ns-user []
                            :all-user [{::inc 1 ::times 2}]}))

            (k/invoke d :api/test {:data {:x 2}}) => 6))

        (fact "are executed in order 2/2"
          (let [d (k/dispatcher
                    {:handlers {:api (k/handler
                                       {:name :test
                                        ::inc 1
                                        ::times 2}
                                       (p/fn-> :data :x))}
                     :user [[::times times*]
                            [::inc inc*]]})]

            (fact "user-meta is populated correctly"
              (k/some-handler d :api/test)
              => (contains {:user {::inc 1 ::times 2}
                            :ns-user []
                            :all-user [{::inc 1 ::times 2}]}))

            (k/invoke d :api/test {:data {:x 2}}) => 5)))

      (fact "namespace-meta"
        (fact "are executed in order 2/2"
          (let [api-ns (k/namespace
                         {:name :api
                          ::inc 1
                          ::times 2})
                d (k/dispatcher
                    {:handlers {api-ns (k/handler
                                         {:name :test}
                                         (p/fn-> :data :x))}
                     :user [[::inc inc*]
                            [::times times*]]})]

            (fact "user-meta is populated correctly"
              (k/some-handler d :api/test)
              => (contains {:user {}
                            :ns-user [{::inc 1 ::times 2}]
                            :all-user [{::inc 1 ::times 2}]}))

            (k/invoke d :api/test {:data {:x 2}}) => 6))

        (fact "are executed in order 1/2"
          (let [api-ns (k/namespace
                         {:name :api
                          ::inc 1
                          ::times 2})
                d (k/dispatcher
                    {:handlers {api-ns (k/handler
                                         {:name :test}
                                         (p/fn-> :data :x))}
                     :user [[::times times*]
                            [::inc inc*]]})]

            (fact "user-meta is populated correctly"
              (k/some-handler d :api/test)
              => (contains {:user {}
                            :ns-user [{::inc 1 ::times 2}]
                            :all-user [{::inc 1 ::times 2}]}))

            (k/invoke d :api/test {:data {:x 2}}) => 5))))))

(facts "all-handlers, available-handlers & dispatch-handlers"
  (let [handler->action (fn [m] (p/for-map [[k v] m] (:action k) v))
        require-admin? (contains {:required #{:admin}})
        invalid-input? (contains {:type ::k/request})]
    (let [admin-ns (k/namespace {:name :admin, ::roles! #{:admin}})
          secret-ns (k/namespace {:name :secret, ::roles #{:admin}})
          handler1 (k/handler {:name :handler1} (p/fnk [] true))
          handler2 (k/handler {:name :handler2} (p/fnk [[:data x :- s/Bool]] x))
          d (k/dispatcher {:user {::roles! require-role!
                                  ::roles require-role}
                           :handlers {:api {admin-ns [handler1 handler2]
                                            secret-ns [handler1 handler2]
                                            :public [handler1 handler2]}}})]

      (fact "there are 6 handlers"
        (k/all-handlers d nil) => (n-of k/handler? 6))

      (fact "4 handlers could be called (as 2 are secret)"
        (handler->action (k/dispatch-handlers d :check nil {}))
        => (just
             {:api.admin/handler1 require-admin?
              :api.admin/handler2 require-admin?
              :api.public/handler1 nil
              :api.public/handler2 nil}))

      (fact "in namespace api"

        (fact "there are 6 handlers"
          (k/all-handlers d :api) => (n-of k/handler? 6))

        (fact "4 handlers are available (2 are secret)"
          (k/available-handlers d nil {}) => (n-of k/handler? 4))

        (fact "4 handlers could be called (as 2 are secret)"
          (handler->action (k/dispatch-handlers d :check :api {}))
          => (just
               {:api.admin/handler1 require-admin?
                :api.admin/handler2 require-admin?
                :api.public/handler1 nil
                :api.public/handler2 nil})))

      (fact "in namespace api.admin"

        (fact "there are 2 handlers"
          (k/all-handlers d :api.admin) => (n-of k/handler? 2))

        (fact "2 handlers are available"
          (k/available-handlers d :api.admin {}) => (n-of k/handler? 2))

        (fact "2 handlers could be called (with errors)"
          (handler->action (k/dispatch-handlers d :check :api.admin {}))
          => (just
               {:api.admin/handler1 require-admin?
                :api.admin/handler2 require-admin?})))

      (fact "in namespace api.secret"

        (fact "there are 2 handlers"
          (k/all-handlers d :api.secret) => (n-of k/handler? 2))

        (fact "0 handlers are available"
          (k/available-handlers d :api.secret {}) => [])

        (fact "no handlers could be called"
          (handler->action (k/dispatch-handlers d :check :api.secret {}))
          => (just {})))

      (fact "in invalid namespace"

        (fact "there are no handlers"
          (k/all-handlers d :api.adm) => [])

        (fact "0 handlers could be called"
          (handler->action (k/dispatch-handlers d :check :api.adm {}))
          => (just {})))

      (facts "dispatch"

        (fact "for anonymous user"
          (let [ctx {}]

            (fact "all apis"

              (k/available-handlers d nil ctx)
              => (n-of k/handler? 4)

              (handler->action (k/dispatch-handlers d :check nil ctx))
              => (just
                   {:api.admin/handler1 require-admin?
                    :api.admin/handler2 require-admin?
                    :api.public/handler1 nil
                    :api.public/handler2 nil})

              (handler->action
                (k/dispatch-handlers d :validate nil ctx))
              => (just
                   {:api.admin/handler1 require-admin?
                    :api.admin/handler2 require-admin?
                    :api.public/handler1 nil
                    :api.public/handler2 invalid-input?}))

            (fact "admin-apis"

              (k/available-handlers d :api.admin ctx)
              => (n-of k/handler? 2)

              (handler->action (k/dispatch-handlers d :check :api.admin ctx))
              => (just
                   {:api.admin/handler1 require-admin?
                    :api.admin/handler2 require-admin?})

              (handler->action (k/dispatch-handlers d :validate :api.admin ctx))
              => (just
                   {:api.admin/handler1 require-admin?
                    :api.admin/handler2 require-admin?}))))

        (fact "for admin-user"
          (let [ctx {::roles #{:admin}}
                ctx2 (merge ctx {:data {:x true}})]

            (fact "all handlers"

              (fact "all handlers are available"
                (k/available-handlers d nil ctx)
                => (n-of k/handler? 6))

              (fact "everything checks ok"
                (handler->action (k/dispatch-handlers d :check nil ctx))
                => (just
                     {:api.admin/handler1 nil
                      :api.admin/handler2 nil
                      :api.public/handler1 nil
                      :api.public/handler2 nil
                      :api.secret/handler1 nil
                      :api.secret/handler2 nil}))

              (fact "validate gives validation errors for missing parameters"
                (handler->action (k/dispatch-handlers d :validate nil ctx))
                => (just
                     {:api.admin/handler1 nil
                      :api.admin/handler2 invalid-input?
                      :api.public/handler1 nil
                      :api.public/handler2 invalid-input?
                      :api.secret/handler1 nil
                      :api.secret/handler2 invalid-input?}))

              (fact "with valid input validate passes ok"
                (handler->action (k/dispatch-handlers d :validate nil ctx2))
                => (just
                     {:api.admin/handler1 nil
                      :api.admin/handler2 nil
                      :api.public/handler1 nil
                      :api.public/handler2 nil
                      :api.secret/handler1 nil
                      :api.secret/handler2 nil})))

            (fact "admin apis"

              (fact "all handlers are available"
                (k/available-handlers d :api.admin ctx)
                => (n-of k/handler? 2))

              (fact "everything checks ok"
                (handler->action (k/dispatch-handlers d :check :api.admin ctx))
                => (just
                     {:api.admin/handler1 nil
                      :api.admin/handler2 nil}))

              (fact "validate gives validation errors for missing parameters"
                (handler->action (k/dispatch-handlers d :validate :api.admin ctx))
                => (just
                     {:api.admin/handler1 nil
                      :api.admin/handler2 invalid-input?}))

              (fact "with valid input validate passes ok"
                (handler->action (k/dispatch-handlers d :validate :api.admin ctx2))
                => (just
                     {:api.admin/handler1 nil
                      :api.admin/handler2 nil}))))

          (fact "interacting with a spesific handler"

            (fact "without required role"
              (let [ctx {}]
                (fact "with missing parameters"
                  (fact "with missing parameters"
                    (k/check d :api.secret/handler2 ctx) => missing-route?
                    (k/validate d :api.secret/handler2 ctx) => missing-route?
                    (k/invoke d :api.secret/handler2 ctx) => missing-route?

                    (fact "with all parameters"
                      (let [ctx (merge ctx {:data {:x true}})]
                        (k/check d :api.secret/handler2 ctx) => missing-route?
                        (k/validate d :api.secret/handler2 ctx) => missing-route?
                        (k/invoke d :api.secret/handler2 ctx) => missing-route?))))))

            (fact "with required role"
              (let [ctx {::roles #{:admin}}]
                (fact "with missing parameters"
                  (k/check d :api.secret/handler2 ctx) => nil
                  (k/validate d :api.secret/handler2 ctx) => input-coercion-error?
                  (k/invoke d :api.secret/handler2 ctx) => input-coercion-error?

                  (fact "with all parameters"
                    (let [ctx (merge ctx {:data {:x true}})]
                      (k/check d :api.secret/handler2 ctx) => nil
                      (k/validate d :api.secret/handler2 ctx) => nil
                      (k/invoke d :api.secret/handler2 ctx) => true)))))))))))

(fact "context transformations"
  (let [copy-ab-to-cd (k/context-copy [:a :b] [:c :d])
        remove-ab (k/context-dissoc [:a :b])]

    (copy-ab-to-cd {:a {:b 1}}) => {:a {:b 1} :c {:d 1}}
    (remove-ab {:a {:b 1}}) => {}
    ((comp remove-ab copy-ab-to-cd) {:a {:b 1}}) => {:c {:d 1}}))

(fact "Interceptors"
  (let [stop (constantly nil)]
    (fact "functions can be expanded into interceptors"
      (k/interceptor stop) => {:enter stop})
    (fact "maps as interceptors"
      (k/interceptor {:enter stop}) => {:enter stop}
      (k/interceptor {:leave stop}) => {:leave stop}
      (k/interceptor {:enter stop, :leave stop}) => {:enter stop, :leave stop}
      (fact "invalid keys cause failure"
        (k/interceptor {:enter stop, :whatever stop}) => throws?)
      (fact "either enter or leave is required"
        (k/interceptor {}) => throws?))))

(fact "Interceptors"
  (let [->> (fn [x] (fn [ctx] (update ctx :x #(str % x))))
        <<- (fn [x] (fn [ctx] (update ctx :response #(str % x))))]

    (fact "are executed in order"
      (let [d (k/dispatcher
                {:handlers
                 {:api
                  (k/handler
                    {:name :test
                     :interceptors [{:enter (->> "4"), :leave (<<- "4")}
                                    {:enter (->> "5"), :leave (<<- "5")}
                                    (->> "6")
                                    {:leave (<<- "6")}]}
                    (p/fn-> :x (str "-")))}
                 :interceptors [{:enter (->> "1"), :leave (<<- "1")}
                                {:enter (->> "2"), :leave (<<- "2")}
                                (->> "3")
                                {:leave (<<- "3")}]})]

        (k/invoke d :api/test) => "123456-654321"))

    (fact "returning nil on :enter stops the execution"
      (let [d (k/dispatcher
                {:handlers {:api (k/handler {:name :test} (p/fn-> :x))}
                 :interceptors [(constantly nil)
                                #(throw AssertionError)]})]

        (k/invoke d :api/test) => missing-route?))

    (fact "returning nil on :leave stops the execution"
      (let [d (k/dispatcher
                {:handlers {:api (k/handler {:name :test} (p/fn-> :x))}
                 :interceptors [{:leave #(throw AssertionError)}
                                {:leave (constantly nil)}]})]

        (k/invoke d :api/test) => missing-route?))))

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

(facts "interceptors requiring parameters"
  (let [str->int-matcher {s/Int (fn [x] (if (string? x) (Long/parseLong x) x))}
        load-doc (constantly
                   (p/fnk [[:data doc-id :- s/Int] :as ctx]
                     (assoc-in ctx [:entity :doc] (-> ctx :docs (get doc-id)))))
        secret-ns (k/namespace {:name :secret, ::roles #{:admin}})
        doc-ns (k/namespace {:name :doc ::load-doc true})
        read (k/handler {:name :read} (p/fnk [[:entity doc :- s/Str] :as ctx]
                                        ;; despite we haven't defined [:data :doc-id], it already coerced!
                                        (assert (-> ctx :data :doc-id class (= Long)))
                                        {:read doc}))
        d (k/dispatcher {:user {::roles require-role
                                ::load-doc load-doc}
                         :coercion {:input {:data str->int-matcher}}
                         :context {:docs {1 "hello ruby"
                                          2 "land of lisp"}}
                         :handlers {:api {secret-ns {doc-ns read}}}})]

    (fact "input schemas have been modified"
      (let [handler (k/some-handler d :api.secret.doc/read)]

        (fact "handler-input is coming directly from handler"
          handler => (contains
                       {:handler-input {:entity {:doc s/Str, s/Keyword s/Any}
                                        s/Keyword s/Any}}))

        (fact "user-input is accumulated from the path"
          handler => (contains
                       {:user-input {:data {:doc-id s/Int, s/Keyword s/Any}
                                     s/Keyword s/Any}}))

        (fact "input is merged sum of the previous"
          handler => (contains
                       {:input {:entity {:doc s/Str, s/Keyword s/Any}
                                :data {:doc-id s/Int, s/Keyword s/Any}
                                s/Keyword s/Any}}))))

    (fact "with invalid credentials"
      (k/invoke d :api.secret.doc/read {}) => missing-route?)

    (fact "with valid credentials"
      (fact "data in correct format"
        (k/invoke d :api.secret.doc/read {:data {:doc-id 1} ::roles #{:admin}})
        => {:read "hello ruby"})

      (fact "data in wrong format gets coerced already with ns metas"
        (k/invoke d :api.secret.doc/read {:data {:doc-id "1"} ::roles #{:admin}})
        => {:read "hello ruby"})

      (fact "data in wrong format and can't get fixed"
        (k/invoke d :api.secret.doc/read {:data {:doc-id true} ::roles #{:admin}})
        => input-coercion-error?))

    (fact "with input-coercion disabled"
      (let [d (k/dispatcher {:user {::roles require-role
                                    ::load-doc load-doc}
                             :coercion {:input nil}
                             :context {:docs {1 "hello ruby"
                                              2 "land of lisp"}}
                             :handlers {:api {secret-ns {doc-ns read}}}})]

        (fact "with valid credentials"

          (fact "data in correct format"
            (k/invoke d :api.secret.doc/read {:data {:doc-id 1} ::roles #{:admin}})
            => {:read "hello ruby"})

          (fact "data in wrong format and can't get fixed"
            (k/invoke d :api.secret.doc/read {:data {:doc-id "1"} ::roles #{:admin}})
            => (throws? {:type ::s/error})))))))

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
                                    (p/<- (k/all-handlers nil))
                                    (map :name)
                                    set)))]}})]

    (fact "::dispatcher"
      (s/validate Dispatcher (k/invoke d :api/dispatcher)) => (partial instance? Dispatcher))

    (fact "::handler"
      (k/invoke d :api/handler) => (contains {:description "metameta"}))

    (fact "going meta boing boing"
      (k/invoke d :api/names) => #{:dispatcher :handler :names})))

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
                          (p/fnk ^:never-validate plus :- {:result PositiveInt}
                            [[:data x :- s/Int, y :- PositiveInt]]
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
                :schema {:data {:x s/Int, :y PositiveInt, s/Keyword s/Any}}}))

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
            => {:result -10})))

      (facts "with input coercion for another key"
        (let [d (k/dispatcher {:handlers {:api [(k/handler
                                                  {:name :plus}
                                                  (p/fnk plus [[:data x :- s/Int]
                                                               [:tada y :- s/Int]]
                                                    {:result (+ x y)}))]}
                               :coercion {:input {:data (constantly nil)
                                                  :tada (constantly nil)}}})]

          (fact "with invalid input"
            (k/invoke d :api/plus {:data {:x 1}})
            => input-coercion-error?)

          (fact "with invalid input"
            (k/invoke d :api/plus {:tada {:y 1}})
            => input-coercion-error?)

          (fact "with valid input"
            (k/invoke d :api/plus {:data {:x 1}
                                   :tada {:y 1}})
            => {:result 2}))))))

(facts "context-based coercion"
  (let [str->long-matcher {s/Int (fn [x] (if (string? x) (Long/parseLong x) x))}
        str->long-coercion (fn [schema value]
                             (k/coerce! schema str->long-matcher value ..in.. ..type..))
        single-coercion (k/simple-coercion str->long-matcher)
        multi-coercion (k/multi-coercion {:data str->long-coercion})
        handler (k/handler
                  {:name :test
                   :output {:value s/Int}}
                  (fn [context]
                    (:data (k/input-coerce! context {:data {:value s/Int
                                                            s/Keyword s/Any}
                                                     s/Keyword s/Any}))))]

    (facts "manual coercion"
      (facts "with input coercion on"
        (let [d (k/dispatcher {:handlers {:api handler}})]

          (fact "with request-coercion off, default input coercion is used"
            (k/invoke d :api/test {:data {:value "123"}}) => input-coercion-error?)

          (fact "with request-coercion on"
            (k/invoke d :api/test {:data {:value "123"}
                                   ::k/coercion multi-coercion}) => {:value 123}

            (k/invoke d :api/test {:data {:value "123"}
                                   ::k/coercion single-coercion}) => {:value 123})

          (fact "with ineffective request-coercion"
            (k/invoke d :api/test {:data {:value false}
                                   ::k/coercion multi-coercion}) => (throws? {:in ..in.., :type ..type..})

            (k/invoke d :api/test {:data {:value false}
                                   ::k/coercion single-coercion}) => input-coercion-error?)))

      (facts "witn input coercion off"
        (let [d (k/dispatcher {:handlers {:api handler}
                               :coercion {:input nil}})]

          (fact "with request-coercion off"
            (k/invoke d :api/test {:data {:value "123"}}) => output-coercion-error?))))

    (fact "automatic endpoint coercion"
      (let [d (k/dispatcher {:handlers {:api (k/handler
                                               {:name :test}
                                               (p/fnk ^:never-validate f :- {:value s/Int}
                                                 [data :- {:value s/Int}]
                                                 data))}
                             :coercion {:input nil}})]

        (fact "with request-coercion off"
          (k/invoke d :api/test {:data {:value "123"}}) => output-coercion-error?)

        (fact "with request-coercion on"
          (k/invoke d :api/test {:data {:value "123"}
                                 ::k/coercion multi-coercion}) => {:value 123}

          (k/invoke d :api/test {:data {:value "123"}
                                 ::k/coercion single-coercion}) => {:value 123})

        (fact "with ineffective request-coercion"
          (k/invoke d :api/test {:data {:value false}
                                 ::k/coercion multi-coercion}) => (throws? {:in ..in.., :type ..type..})

          (k/invoke d :api/test {:data {:value false}
                                 ::k/coercion single-coercion}) => input-coercion-error?)))

    (fact "automatic path coercion"
      (let [api (k/namespace {:name :api ::load-doc true})
            d (k/dispatcher {:user {::roles require-role
                                    ::load-doc (constantly
                                                 (p/fnk ^:never-validate f :- {:value s/Int}
                                                   [[:data value :- s/Int] :as ctx]
                                                   ctx))}
                             :coercion {:input nil}
                             :handlers {api [(k/handler
                                               {:name :test
                                                :output {:value s/Int}}
                                               (fn [context] (:data context)))
                                             (k/handler
                                               {:name :test2}
                                               (p/fnk [[:data x :- s/Str :as data]] data))]}})]

        (pr-str (:input (k/some-handler d :api/test2)))

        (fact "with request-coercion off"
          (k/invoke d :api/test {:data {:value "123"}}) => output-coercion-error?)

        (fact "with request-coercion on"
          (k/invoke d :api/test {:data {:value "123"}
                                 ::k/coercion multi-coercion}) => {:value 123}

          (k/invoke d :api/test {:data {:value "123"}
                                 ::k/coercion single-coercion}) => {:value 123})

        (fact "with ineffective request-coercion"
          (k/invoke d :api/test {:data {:value false}
                                 ::k/coercion multi-coercion}) => (throws? {:in ..in.., :type ..type..})

          (k/invoke d :api/test {:data {:value false}
                                 ::k/coercion single-coercion}) => input-coercion-error?)))))

(fact "printing it"
  (pr-str (k/dispatcher {:handlers {}})) => "#<Dispatcher>")
