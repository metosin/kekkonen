(ns kekkonen.interceptor-test
  (:require [midje.sweet :refer :all]
            [kekkonen.interceptor :as i]))

(defn not-executed [_]
  (throw (ex-info "not-run" {})))

(facts "interceptor?"
  (i/interceptor? {:enter identity}) => true
  (i/interceptor? {:leave identity}) => true
  (i/interceptor? {:error identity}) => true
  (i/interceptor? {}) => false)

(facts "queues"
  (let [interceptor1 {:enter identity, :leave identity}
        interceptor2 {:enter identity, :leave identity}]
    (fact "enqueue"
      (i/enqueue {} [interceptor1]) => {::i/queue [interceptor1]}
      (i/enqueue {} [interceptor1 interceptor2]) => {::i/queue [interceptor1 interceptor2]})
    (fact "terminate"
      (i/terminate (i/enqueue {} [interceptor1 interceptor2])) => {})))

(facts "execute"
  (facts "are executed in order"
    (-> {:x 2}
        (i/enqueue [{:enter #(update % :x inc)}
                    {:enter #(update % :x (partial * 2))
                     :leave #(update % :x (partial * 2))}
                    {:leave #(update % :x dec)}])
        (i/execute)) => {:x 10})

  (fact "with terminate, future steps are not executed"
    (-> {:x 2}
        (i/enqueue [{:enter #(update % :x inc)
                     :leave #(update % :x inc)}
                    {:enter i/terminate
                     :leave #(update % :x (partial * 2))}
                    {:leave not-executed}])
        (i/execute)) => {:x 7})

  (fact "setting context to nil, all execution is stopped"
    (-> {:x 2}
        (i/enqueue [{:enter #(update % :x inc)
                     :error (fn [context e]
                              (println context "-" e))
                     :leave not-executed}
                    {:enter (constantly nil)
                     :leave not-executed}
                    {:leave not-executed}])
        (i/execute)) => nil)

  (fact "on exception"
    (fact "can be caught"
      (-> {:x 2}
          (i/enqueue [{:enter #(update % :x inc)
                       :error (fn [context e]
                                (assoc context ::exception true))
                       :leave not-executed}
                      {:enter (fn [_] (throw (ex-info "fail" {:reason "too many men"})))
                       :leave not-executed}
                      {:leave not-executed}])
          (i/execute))) => {:x 3 ::exception true}))
