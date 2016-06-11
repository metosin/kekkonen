(ns get-post.core
  (:require [org.httpkit.server :as server]
            [plumbing.core :refer [defnk]]
            [kekkonen.cqrs :as cqrs]
            [kekkonen.core :as k]
            [schema.core :as s]))

(s/defschema GetInput
             {:name                         s/Str
              (s/optional-key :description) s/Str})

(s/defn get-handler
        "Echoes a GetInput"
        [data :- GetInput]
        ; here is your handler
        (cqrs/success data))

(s/defschema PostInput
             {:data s/Str})

(s/defn post-handler
        "Echoes a PostInput"
        [data :- PostInput]
        ; here is your handler
        (cqrs/success data))

(defnk ^:get-post get-and-post
       "handles both requests"
       [get-params post-params request]
       (if (= (:request-method request) :get)
         (s/with-fn-validation (get-handler get-params))
         (s/with-fn-validation (post-handler post-params))))

(defn interceptor [ctx]
  "logs incoming requests for us"
  (let [uri (get-in ctx [:request :uri])
        request-method (name (get-in ctx [:request :request-method]))]
    (println (str request-method ": " uri))
    ctx))

(defn err-handler [ex data req]
  "logs exception message and return info to client"
  (println (str "ERROR: " (.getMessage ex)))
  (cqrs/failure (.getMessage ex)))

(def app (cqrs/cqrs-api {:core {:handlers      {:api [#'get-and-post]}
                                :type-resolver (k/type-resolver :get :get-post :post)}
                         :mw   {:exceptions {:handlers {:schema.core/error err-handler}}}
                         :ring {:types        {:get-post {:methods    #{:get :post}
                                                          ; :query-params comes from Ring https://github.com/ring-clojure/ring/wiki/Parameters
                                                          :parameters {[:get-params]  [:request :query-params]
                                                                       [:post-params] [:request :body-params]}}}
                                :interceptors [interceptor]}}))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [&args]
  (reset! server (server/run-server #'app {:port 3000}))
  (println "server running in port 3000"))

(defn run-server []
  (reset! server (server/run-server #'app {:port 3000}))
  (println "server running in port 3000"))