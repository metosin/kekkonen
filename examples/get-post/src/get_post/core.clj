(ns get-post.core
  (:require [org.httpkit.server :as server]
            [plumbing.core :refer [defnk]]
            [kekkonen.http :refer :all]
            [kekkonen.cqrs :as cqrs]
            [kekkonen.core :as k]
            [schema.core :as s]))

(s/defschema GetInput
             {:name s/Str
              (s/optional-key :description) s/Str})

(s/defn get-handler
         "Echoes a GetInput"
         [data :- GetInput]
         (cqrs/success data))


(s/defschema PostInput
             {:data s/Str})

(s/defn post-handler
        "Echoes a PostInput"
        [data :- PostInput]
        (cqrs/success data))

(defnk ^:get-post get-and-post
       "handles both"
       [get-params post-params request]
       (clojure.pprint/pprint request)
       (if (= (:request-method request) :get)
         (s/with-fn-validation (get-handler get-params))
         (s/with-fn-validation (post-handler post-params))))

(def app (http-api {:core {:handlers {:api [#'get-and-post]}
                           :type-resolver (k/type-resolver :get :get-post :post)}
                    :ring {:types {:get-post {:methods #{:get :post}
                                         ; :query-params comes from Ring https://github.com/ring-clojure/ring/wiki/Parameters
                                         :parameters {[:get-params] [:request :query-params]
                                                      [:post-params] [:request :body-params]}}}}}))

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