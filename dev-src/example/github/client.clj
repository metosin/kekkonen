(ns example.github.client
  (:require [kekkonen.client.cqrs :as k]))

(comment
  (def context (k/create "http://localhost:3000"))

  (./aprint context)

  (./aprint
    (:body (k/query context :/api/a/b/c/ping)))

  (./aprint
    (:body (k/query context :/api/calculator/plus)))

  (./aprint
    (:body (k/query context :/api/calculator/plus {:x 1})))

  (./aprint
    (:body (k/query context :/api/calculator/plus {:x 1, :y 2})))

  (def context2 (k/context context {:x 1}))

  (./aprint context2)

  (./aprint
    (:body (k/query context2 :/api/calculator/plus {:y 2})))

  (def context3 (k/context context2 {:y 2}))

  (./aprint
    (:body (k/query context3 :/api/calculator/plus)))

  ;;
  ;; Special thingies
  ;;

  (./aprint
    (:body (k/query context3 :/kekkonen/all)))

  (./pprint
    (map (juxt :action :type) (:body (k/query context3 :/kekkonen/all))))

  (./pprint
    (map (juxt :action :type) (:body (k/query context3 :/kekkonen/available))))

  #_(./pprint
      (map (juxt :action :type) (:body (k/query context3 :/kekkonen/invokable)))))



[:div.col-md-3
 [ui/btn-primary
  :on-click (disaptch ::boss-move)
  :enabled (and (has-role? user :boss)
                (has-access? user repository)
                (some-other-thingie? user))]]

[:div.col-md-3
 [ui/btn-primary
  :on-click (disaptch ::boss-move)
  :enabled (k/validate k ::boss-move)]]


