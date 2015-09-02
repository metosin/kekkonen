(ns kekkonen.ring
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]))

(s/defn uri->action [path :- s/Str]
  (-> path (subs 1) keyword))

(def default-options {:types {:handler {:methods #{:post}}}})

(s/defn ring-handler
  "Creates a ring handler from Kekkonen"
  ([kekkonen]
    (ring-handler kekkonen {}))
  ([kekkonen options]
    (let [options (kc/deep-merge default-options options)]
      (fn [{:keys [request-method uri] :as request}]
        (let [action (uri->action uri)]
          (if-let [handler (k/some-handler kekkonen action)]
            (if-let [type-config (get (:types options) (:type handler))]
              (if-let [method-matches? (:methods type-config)]
                (if (method-matches? request-method)
                  (k/invoke kekkonen action {:request request}))))))))))
