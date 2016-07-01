(ns sample.handler
  (:require [plumbing.core :refer [defnk]]
            [kekkonen.cqrs :refer :all]
            [kekkonen.upload :as upload]
            [schema.core :as s]))

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :S :M :L)
   :origin {:country (s/enum :FI :PO)}})

;;
;; Handlers
;;

(defnk ^:query ping []
  (success {:ping "pong"}))

(defnk ^:command echo-pizza
  "Echoes a pizza"
  {:responses {:default {:schema Pizza}}}
  [data :- Pizza]
  (success data))

(defnk ^:query plus
  "playing with data"
  [[:data x :- s/Int, y :- s/Int]]
  (success (+ x y)))

(defnk ^:command inc!
  "a stateful counter"
  [[:state counter]]
  (success (swap! counter inc)))

(defnk ^:command upload
  "Upload a file to a server"
  {:interceptors [[upload/multipart-params]]}
  [[:state file]
   [:request [:multipart-params upload :- upload/TempFileUpload]]]
  (reset! file upload)
  (success (dissoc upload :tempfile)))

(defnk ^:query download
  "Download the file from the server"
  [[:state file]]
  (let [{:keys [tempfile content-type filename]} @file]
  (upload/response tempfile content-type filename)))

;;
;; Application
;;

(defn create [system]
  (cqrs-api
    {:swagger {:ui "/"
               :spec "/swagger.json"
               :data {:info {:title "Kekkonen sample API"
                             :description "created with http://kekkonen.io"}}}
     :core {:handlers {:pizza #'echo-pizza
                       :math [#'inc! #'plus]
                       :ping #'ping
                       :file [#'upload #'download]}
            :context system}}))
