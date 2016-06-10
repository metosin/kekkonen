(ns get-post.core
  (:require [org.httpkit.server :as server]
            [plumbing.core :refer [defnk]]
            [kekkonen.http :refer :all]
            [kekkonen.cqrs :as cqrs]
            [kekkonen.core :as k]))

(defnk ^:get-post get-and-post
       "handles both"
       {:responses {:default {:schema {:message String}}}}
       [data1 data2 #_[:data name :- String] request]
       (clojure.pprint/pprint request)
       (if (= (:request-method request) :get)
         (cqrs/success {:message (str "Hello GET, " name)})
         (cqrs/success {:message (str "Hello POST, " name)})))

(def app (http-api {:core {:handlers {:api [#'get-and-post]}
                           :type-resolver (k/type-resolver :get :get-post :post)}
                    :ring {:types {:get-post {:methods #{:get :post}
                                         ; :query-params comes from Ring https://github.com/ring-clojure/ring/wiki/Parameters
                                         :parameters {[:data1] [:request :query-params]
                                                      [:data2] [:request :body-params]}}}}}))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [&args]
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and http://http-kit.org/migration.html#reload
  (reset! server (server/run-server #'app {:port 3000}))
  (println "server running in port 3000"))

(defn run-server []
  (reset! server (server/run-server #'app {:port 3000}))
  (println "server running in port 3000"))