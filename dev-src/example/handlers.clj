(ns example.handlers
  (:require [org.httpkit.server :as server]
            [plumbing.core :as p]
            [ring.util.http-response :refer [ok]]
            [kekkonen.ring :as r]
            [kekkonen.core :as k]
            [kekkonen.middleware :as mw]
            [schema.core :as s]))

(p/defnk ^:handler ping [] (ok {:ping "pong"}))

(p/defnk ^:handler snoop
  [request] (ok (dissoc request :body)))

(p/defnk ^:handler echo
  [[:request [:body-params x :- s/Str]]]
  (ok {:x x}))

(def app
  (mw/wrap-api
    (r/ring-handler
      (k/dispatcher
        {:handlers {:api 'example.handlers}}))))

(defn start []
  (server/run-server #'app {:port 3000}))

(comment
  (start))
