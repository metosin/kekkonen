(ns example.http
  (:require [org.httpkit.server :as server]
            [kekkonen.http :as http]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [kekkonen.upload :as upload]))

(defnk ^:get plus
  {:responses {:default {:schema {:result s/Int}}}}
  [[:request [:query-params x :- s/Int, y :- s/Int]]]
  (ok {:result (+ x y)}))

(defnk ^:post times
  {:responses {:default {:schema {:result s/Int}}}}
  [[:request [:body-params x :- s/Int, y :- s/Int]]]
  (ok {:result (* x y)}))

(defnk ^:put minus
  {:responses {:default {:schema {:result s/Int}}}}
  [[:request [:header-params x :- s/Int, y :- s/Int]]]
  (ok {:result (- x y)}))

(defnk ^:put upload
  "upload a file to the server"
  {:interceptors [[upload/multipart-params]]}
  [[:request [:multipart-params file :- upload/TempFileUpload]]]
  (ok (dissoc file :tempfile)))

(def app
  (http/http-api
    {:swagger {:ui "/"
               :spec "/swagger.json"
               :data {:info {:title "HTTP API example"}}}
     :core {:handlers {:file #'upload
                       :math [#'plus #'times #'minus]}}}))

(comment
  (server/run-server #'app {:port 3000}))
