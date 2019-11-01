;; Original: https://github.com/metosin/ring-swagger/blob/master/src/ring/swagger/upload.clj
(ns kekkonen.upload
  (:require [schema.core :as s]
            [ring.swagger.json-schema :as js]
            [ring.middleware.multipart-params :as multipart-params]
            [clojure.walk :as walk])
  (:import [java.io File ByteArrayInputStream]))

(defn multipart-params
  ([]
   (multipart-params {}))
  ([options]
   {:enter (fn [ctx]
             (update
               ctx
               :request
               (fn [request]
                 (-> request
                     (multipart-params/multipart-params-request options)
                     (update :multipart-params walk/keywordize-keys)))))}))

(defrecord Upload [m]

  s/Schema
  (spec [_]
    (s/spec m))
  (explain [_]
    (cons 'file m))

  js/JsonSchema
  (convert [_ _]
    {:type "file"}))

(def TempFileUpload
  "Schema for file param created by ring.middleware.multipart-params.temp-file store."
  (->Upload {:filename s/Str
             :content-type s/Str
             :size s/Int
             (s/optional-key :tempfile) File}))

(def ByteArrayUpload
  "Schema for file param created by ring.middleware.multipart-params.byte-array store."
  (->Upload {:filename s/Str
             :content-type s/Str
             :bytes s/Any}))

(defn response
  "Returns a file response out of File or byte[] content"
  ([content content-type]
   (response content content-type nil))
  ([content content-type filename]
   (let [body (if (instance? File content) content (ByteArrayInputStream. content))]
     {:status 200
      :headers (merge
                 {"Content-Type" content-type}
                 (if filename
                   {"Content-Disposition" (str "inline; filename=\"" filename "\"")}))
      :body body})))
