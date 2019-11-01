(ns kekkonen.upload-test
  (:require [midje.sweet :refer :all]
            [kekkonen.upload :as upload]
            [ring.core.protocols :refer [StreamableResponseBody]]))

(defn- response-body? [x] (satisfies? StreamableResponseBody x))

(facts "response"
       (upload/response (.getBytes "hello" "UTF-8") "text/plain")
       => (just {:status 200,
                 :body response-body?,
                 :headers {"Content-Type" "text/plain"}}))
