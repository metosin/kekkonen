(ns meve.core-test
  (:use meve.core
        midje.sweet))

(defn set-foo [next-handler]
  (fn [request]
    (next-handler (assoc-in request [:body :foo] "foo"))))

(defn set-bar [next-handler]
  (fn [request]
    (next-handler (assoc-in request [:body :foo] "bar"))))

(defcommand hello
  [set-foo set-bar]
  [foo]
  {:status 200
   :foo foo})
  
(fact (hello {}) => {:status 200 :foo "bar"})
