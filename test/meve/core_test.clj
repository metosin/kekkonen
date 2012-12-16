(ns meve.core-test
  (:use meve.core
        midje.sweet))

(facts "facts about to-before-fn"
       (let [pass (fn [req] req)
             add-foo (fn [req] (assoc req :foo "Foo"))
             add-bar (fn [req] (assoc req :foo "Bar"))
             err (fn [req] [:error "too tired"])
             fail (fn [req] (throw (Exception. "Should not call this")))]
    (fact ((to-before-fn nil) {}) => {})
    (fact ((to-before-fn []) {}) => {})
    (fact ((to-before-fn [pass pass pass]) {}) => {})
    (fact ((to-before-fn [pass add-foo pass add-bar]) {}) => {:foo "Bar"})
    (fact ((to-before-fn [pass add-foo err fail]) {}) => [:error "too tired"])))

(facts "facts about to-after-fn"
  (let [pass (fn [req resp] resp)
        retry (fn [req resp] :retry)
        err (fn [req resp] [:error "too tired"])
        fail (fn [req resp] (throw (Exception. "Should not call this")))
        add-foo (fn [req resp] (assoc resp :foo "Foo"))
        add-bar (fn [req resp] (assoc resp :foo "Bar"))]
    (fact ((to-after-fn nil) {} :resp) => :resp)
    (fact ((to-after-fn []) {} :resp) => :resp)
    (fact ((to-after-fn [pass pass pass]) {} :resp) => :resp)
    (fact ((to-after-fn [pass retry fail]) {} :resp) => :retry)
    (fact ((to-after-fn [pass err fail]) {} :resp) => [:error "too tired"])
    (fact ((to-after-fn [pass err fail]) {} [:error "gone fishing"]) => [:error "gone fishing"])
    (fact ((to-after-fn [pass add-foo pass add-bar]) {} {}) => {:foo "Bar"})))