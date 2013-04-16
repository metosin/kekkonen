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
  (str "foo=" foo))

(fact (hello {}) => "foo=bar")

;
; Optimistic locking example:
;

(defn find-from-db [id]
  {:id id :version 1 :foo "bar"})

(defn update-db [where update]
  (println "where=" where "update=" update)
  (when (> (:version update) 2)
    (println "ok fine, ")))

(defn with-entity [next-handler]
  (fn [request]
    (if-let [id (get-in request [:body :id])]
      (loop [retry-count 0]
        (let [entity (find-from-db id)
              version (:version entity)
              where {:id id :version version}
              request (assoc request
                             :id id
                             :entity entity
                             :where where
                             :updates []
                             :retry-count retry-count)
              response (next-handler request)]
          (if (update-db
                (merge (:where response) where)
                (assoc (:updates response) :version (inc version)))
            response
            (recur (inc retry-count)))))
      (next-handler request))))

(comment
  (require 'clojure.pprint)
  (clojure.pprint/pprint
    (macroexpand-1
      '(defcommand hello
         [set-foo set-bar]
         [foo]
         {:status 200
          :foo foo}))))


