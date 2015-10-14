(ns kekkonen.http
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [schema.core :as s]
            [kekkonen.ring :as r]))

(s/defn http-api [options]
  (ka/api
    (kc/deep-merge
      {:core {:type-resolver (k/type-resolver :get :head :patch :delete :options :post :put :any)}
       :api {:handlers (r/kekkonen-handlers :get)}
       :ring {:types {:get {:methods #{:get}}
                      :head {:methods #{:head}}
                      :patch {:methods #{:patch}}
                      :delete {:methods #{:delete}}
                      :options {:methods #{:options}}
                      :post {:methods #{:post}}
                      :put {:methods #{:put}}
                      :any {:methods #{:get :head :patch :delete :options :post :put}}}}}
      options)))
