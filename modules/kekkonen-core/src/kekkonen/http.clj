(ns kekkonen.http
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [schema.core :as s]))

(s/defn http-api [options]
  (ka/api
    (kc/deep-merge-map-like
      {:core {:type-resolver (k/type-resolver :get :head :patch :delete :options :post :put :any)}
       :swagger {:data {:info {:title "Kekkonen HTTP API"}}}
       :ring {:types {:get {:methods #{:get}}
                      :head {:methods #{:head}}
                      :patch {:methods #{:patch}}
                      :delete {:methods #{:delete}}
                      :options {:methods #{:options}}
                      :post {:methods #{:post}}
                      :put {:methods #{:put}}
                      :any {:methods #{:get :head :patch :delete :options :post :put}}
                      :handler {:methods #{:post}
                                :parameters {[:data] [:request :body-params]}}}}}
      options)))
