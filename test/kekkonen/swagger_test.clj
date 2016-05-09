(ns kekkonen.swagger-test
  (:require [kekkonen.core :as k]
            [kekkonen.swagger :as ks]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.util.http-response :refer [ok]]
            [plumbing.core :as p]
            [kekkonen.ring :as r]))

(p/defnk ^:handler echo
  {:summary "summary"
   :responses {200 {:schema {:x [s/Str]
                             :y s/Int
                             :z s/Bool}}}}
  [[:request
    body-params :- {:country (s/enum :FI :CA)}
    [:query-params x :- [s/Str]]
    [:path-params y :- s/Int]
    [:header-params z :- s/Bool]]]
  (ok [x y z body-params]))

(fact "swagger-docs"
  (let [dispatcher (k/transform-handlers
                     (k/dispatcher {:handlers {:api {:admin #'echo}}})
                     (partial #'r/attach-ring-meta r/+default-options+))
        handlers (k/available-handlers dispatcher nil {})

        swagger (ks/ring-swagger
                  handlers
                  {:info {:version "1.0.0"
                          :title "Kekkonen"
                          :description "Kekkonen Swagger API"}})]

    (fact "swagger-object is created"

      swagger => {:info {:version "1.0.0"
                         :title "Kekkonen"
                         :description "Kekkonen Swagger API"}
                  :paths {"/api/admin/echo"
                          {:post
                           {:parameters {:body {:country (s/enum :CA :FI)}
                                         :header {:z s/Bool, s/Keyword s/Any
                                                  (s/optional-key "kekkonen.mode") (s/enum "invoke" "validate")}
                                         :path {:y s/Int, s/Keyword s/Any}
                                         :query {:x [s/Str], s/Keyword s/Any}}
                            :responses {200 {:schema {:x [s/Str]
                                                      :y s/Int
                                                      :z s/Bool}}}
                            :summary "summary"
                            :tags [:api.admin]}}}})

    (fact "swagger-json can be generated"
      (s/with-fn-validation
        (ks/swagger-object swagger {}) => some?))))

(facts "swagger-handler"
  (let [dispatcher (k/transform-handlers
                     (k/dispatcher {:handlers {:api {:admin #'echo}}})
                     (partial #'r/attach-ring-meta r/+default-options+))
        swagger-handler (ks/swagger-handler {} {:spec "swagger.json", :info {:version "1.2.3"}})]
    (against-background [(k/get-dispatcher anything) => dispatcher]

                        (fact "generates swagger json"
                          (swagger-handler {}) => (contains {:body (contains {:paths seq})})))

    (fact "extracts swagger basePath from request context"
      (let [context-path "/testpath"]
        (:body (swagger-handler {:request {:context context-path}})) => (contains {:basePath context-path})))

    (fact "does not add basePath if no context"
      (-> (swagger-handler {:request {}}) :body :basePath) => nil)))
