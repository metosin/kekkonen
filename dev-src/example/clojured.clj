(ns example.clojured
  (:require [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [kekkonen.core :as k]
            [clojure.set :as set]
            [kekkonen.ring :as r]
            [kekkonen.swagger :as ks]
            [kekkonen.middleware :as mw]
            [org.httpkit.server :as server]
            [kekkonen.common :as kc]
            [ring.util.http-response :refer [ok]]))

{:name :plus
 :type :handler
 :description "Adds to numbers together"
 :input {:data {:y s/Int, :x s/Int, s/Keyword s/Any}, s/Keyword s/Any}
 :output s/Int
 :function (fn [{{:keys [x y]} :data}]
             (+ x y))}


(+ 1 1)


(defnk ^:handler plus :- s/Int
  "Adds to numbers together"
  [[:data x :- s/Int, y :- s/Int]]
  (+ x y))

(defn plus
  "Adds to numbers together"
  {:type :handler
   :input {:data {:y s/Int
                  :x s/Int
                  s/Keyword s/Any}
           s/Keyword s/Any}
   :output s/Int}
  [{{:keys [x y]} :data}]
  (+ x y))

{:name :plus
 :type :handler
 :description "Adds to numbers together"
 :input {:data {:y s/Int
                :x s/Int
                s/Keyword s/Any}
         s/Keyword s/Any}
 :output s/Int
 :handler (fn [{{:keys [x y]} :data}]
            (+ x y))}

; simple context
{:data {:x 1, :y 1}}

; with ring-request & dependencies
{:data {:x 1, :y 1}
 :request {:request-method :get
           :uri "/api/plus"}
 :system {:db (atom {})}}


{:name :admin
 :type :namespace
 :description "Admin-operations"
 :interceptors [[require-role :admin]]}

{:api
 {:math [plus]
  :admin [clear-db, add-account]}}


(comment
  some-handler, check, validate, invoke, (poke, bundle)
  all-handers, available-handlers, dispatch-handlers)

(defnk ^:handler increment
  "Stateful counter"
  [counter]
  (ok
    (swap! counter inc)))

(defnk ^:handler plus
  "Adds two numbers together"
  [[:data x - s/Int, y :- s/Int]]
  (ok
    (+ x y)))

(def d (k/dispatcher
         {:handlers {:math [#'increment #'plus]}
          :context {:counter (atom 0)}}))

(k/invoke d :math/plus)                                     ; => CoerceionError {:data missing-required-key}
(k/invoke d :math/plus {:data {:x 1}})                      ; => CoerceionError {:data {:y missing-required-key}
(k/invoke d :math/plus {:data {:x 1, :y 2}})                ; => 3

(k/invoke d :math/increment)                                ; => 1
(k/invoke d :math/increment)                                ; => 2
(k/invoke d :math/increment)                                ; => 3

(k/invoke d :math/increment {:counter (atom 41)})           ; => 42

(s/defn require-roles [required :- #{s/Keyword}]
  {:enter
   (fn [context]
     (let [roles (-> context :user :roles)]
       (if (seq (set/intersection roles required))
         context)))})

{:name "logging interceptor"
 :enter (fn [ctx] (log/info ctx) ctx)
 :leave (fn [ctx] (log/info ctx) ctx)}

;;;

(defnk ^:handler ping [] "pong")

(def app (r/ring-handler
           (k/dispatcher {:handlers {:api [#'ping]}})))

(app {:uri "/" :request-method :post})                      ; => nil

(app {:uri "/api/ping" :request-method :post})              ; => "pong"


(require '[kekkonen.api :as a])
(require '[ring.util.http-response :refer :all])

(s/defschema Kebab
  {:name s/Str
   :size (s/enum :S :L)})

(defnk ^:handler ping []
  (ok {:ping "pong"}))

(defnk ^:handler kebabo [data :- Kebab]
  (ok data))

(def app
  (a/api {:core {:handlers {:api [#'ping]}}}))

(server/run-server #'app {:port 3001})

;;;;;

(defn api [options]
  (s/with-fn-validation
    (let [options (s/validate Options (kc/deep-merge +default-options+ options))
          swagger (merge (:swagger options) (mw/api-info (:mw options)))
          dispatcher (-> (k/dispatcher (:core options))
                         (k/inject (-> options :api :handlers))
                         (k/inject (ks/swagger-handler swagger options)))]
      (mw/wrap-api
        (r/routes
          [(r/ring-handler dispatcher (:ring options))
           (ks/swagger-ui (:swagger-ui options))])
        (:mw options)))))

;;;;;

(def success http/ok)
(def failure http/bad-request)
(def error http/internal-server-error)

(defn cqrs-api [options]
  (a/api
    (kc/deep-merge
      {:core {:type-resolver (k/type-resolver :command :query)}
       :swagger {:info {:title "Kekkonen CQRS API"}}
       :ring {:types {:query {:methods #{:get}
                              :parameters {[:data] [:request :query-params]}}
                      :command {:methods #{:post}
                                :parameters {[:data] [:request :body-params]}}}}}
      options)))

;;;;

(s/defschema Kebab
  {:id s/Int
   :name s/Str
   :type (s/enum :doner :sish :souvlaki)})

(s/defschema NewKebab
  (dissoc Kebab :id))

;;
;; Commands & Queries
;;

(defnk ^:query get-kebabs
  "Retrieves all kebabs"
  {:responses {:default {:schema [Kebab]}}}
  [[:system db]]
  (success (vals @db)))

(defnk ^:command add-kebab
  "Adds an kebab to database"
  {:responses {:default {:schema Kebab}}}
  [[:system db ids], data :- NewKebab]
  (let [item (assoc data :id (swap! ids inc))]
    (swap! db assoc (:id item) item)
    (success item)))

#_(defnk ^:command add-kebab
    "Adds an kebab to database"
    {:responses {:default {:schema Kebab}}}
    [[:system db ids]
     data :- NewKebab]

    (if (-> data :type (= :mustamakkara))
      (failure "Oh nous, not a Kebab!")
      (let [item (assoc data :id (swap! ids inc))]
        (swap! db assoc (:id item) item)
        (success item))))

(defn- forward [dispatcher action ctx data]
  (try
    (k/invoke dispatcher action (assoc ctx :data data))
    (catch Exception e
      (ex-data e))))

(defnk ^:command speculative
  "Dummy implementation. In real life, use a real TX system such as the RDB"
  {:summary "Runs a speculative transaction."}
  [[:system db ids]
   [:data action :- s/Keyword, data :- s/Any]
   :as ctx]

  (let [db' (atom @db)
        ids' (atom @ids)
        ctx' (assoc ctx :system {:db db', :ids ids'})

        dispatcher (k/get-dispatcher ctx)
        response (forward dispatcher action ctx' data)]

    response))

(defnk ^:command transact
  "Dummy implementation. In real life, use a real TX system such as the RDB"
  {:summary "Runs multiple commands in a single transaction."}
  [[:system db ids]
   [:data commands :- [{:action s/Keyword
                        :data s/Any}]] :as ctx]

  (let [db' (atom @db)
        ids' (atom @ids)
        ctx' (assoc ctx :system {:db db', :ids ids'})

        dispatcher (k/get-dispatcher ctx)
        responses (map
                    (fnk [action data]
                         (forward dispatcher action ctx' data))
                    commands)

        {successed true, failed false} (group-by success? responses)
        should-commit? (not (seq failed))
        response (if should-commit? success failure)]

    (when should-commit?
      (reset! db @db')
      (reset! ids @ids'))

    (response {:success successed
               :failed failed})))

;;
;; Application
;;

(def app
  (cqrs-api
    {:core {:handlers {:kebab [#'get-kebabs #'add-kebab]
                       #_#_:tx [#'transact #'speculative]}
            :context {:system {:db (atom {})
                               :ids (atom 0)
                               :counter (atom 0)}}}}))

(comment
  (server/run-server #'app {:port 4001}))

;;;

(defnk ^:command close-application
  "Closes the application if "
  {:roles #{:applicant}
   :states #{:open :draft}
   :interceptors [log-command]}
  [[:system db] [:data id :- s/Int]]
  (success (application/close db id)))



(def app
  (r/ring-handler
    (k/dispatcher
      {:handlers {:math [#'increment #'plus]}
       :context {:counter (atom 0)}})))

(app {:uri "/", :request-method :get}) => nil

(app {:uri "/math/plus"
      :request-method :post
      :body-params {:x 1, :y 2}}) => 3

(require '[kekkonen.api :as a])

(def app
  (kekkonen.api/api
    {:core {:handlers {:math [#'increment #'plus]}
            :context {:counter (atom 0)}}}))

(server/run-server #'app {:port 5000})

;;;;;


(defquery link-permit-required
          {:description "Dummy command for UI logic: returns falsey if link permit is not required."
           :parameters [:id]
           :user-roles #{:applicant :authority}
           :states states/pre-sent-application-states
           :pre-checks [(fn [_ application]
                          (when-not (a/validate-link-permits application)
                            (fail :error.link-permit-not-required)))]})

;;;

(defnk ^:command claim
  "Claim this permit"
  {:requires-role #{:authority}
   ::requires-claim :no
   ::retrieve-permit true
   :interceptors [broadcast-update]}
  [[:state permits]
   [:entities
    [:permit permit-id]
    [:current-user user-id]]]
  (swap! permits update permit-id assoc :authority-id user-id)
  (success {:status :ok}))

(defnk ^:command approve
  "Approve a permit"
  {:requires-role #{:authority}
   ::requires-claim true
   ::retrieve-permit true
   ::requires-state #{:submitted}
   :interceptors [broadcast-update]}
  [[:state permits archive-id-seq]
   [:entities [:permit permit-id]]]
  (swap! permits update permit-id assoc
         :state :approved
         :archive-id (swap! archive-id-seq inc))
  (success {:status :ok}))

;;;;

(defn create-api [{:keys [state chord]}]
  (cqrs-api
    {:swagger {:info {:title "Building Permit application"
                      :description "a complex simulated real-life case example showcase project for http://kekkonen.io"}
               :securityDefinitions {:api_key {:type "apiKey", :name "x-apikey", :in "header"}}}
     :swagger-ui {:validator-url nil
                  :path "/api-docs"}
     :core {:handlers {building-permit-ns 'backend.building-permit
                       users-ns 'backend.users
                       :session 'backend.session}
            :user [[:require-session app-session/require-session]
                   [:load-current-user app-session/load-current-user]
                   [:requires-role app-session/requires-role]
                   [::building-permit/retrieve-permit building-permit/retrieve-permit]
                   [::building-permit/requires-state building-permit/requires-state]
                   [::building-permit/requires-claim building-permit/requires-claim]]
            :context {:state state
                      :chord chord}}}))

