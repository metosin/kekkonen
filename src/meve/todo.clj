(ns meve.todo
  (:use [meve.core :only [defcommand]]
        [meve.log]))

(defcommand hello
  {}
  [foo bar]
  (println "inside" request)
  {:hello "Hullo"
   :foo foo
   :bar request})

(hello {:body {:foo "Foozzaa"}})

(clojure.pprint/pprint
  (macroexpand-1
    '(defcommand hello
      {:validators []}
      [foo bar]
      (println "inside" request)
      {:hello "Hullo"
       :foo foo
       :bar request})))

(comment
  
  (hello {:body {:foo "foozz"}})
  
  (require 'clojure.pprint)
  (clojure.pprint/pprint (macroexpand-1 '(defcommand hello {} [foo] {:foo foo}))))
