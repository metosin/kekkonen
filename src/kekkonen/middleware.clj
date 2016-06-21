(ns kekkonen.middleware
  (:require [ring.middleware.format-params :refer [wrap-restful-params]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            ring.middleware.http-response
            [ring.util.http-response :as r]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [slingshot.slingshot :as slingshot]
            [kekkonen.common :as kc]
            [kekkonen.impl.logging :as logging]
            [clojure.walk :as walk]
            [schema.utils :as su]
            [schema.core :as s])
  (:import [com.fasterxml.jackson.core JsonParseException]
           [org.yaml.snakeyaml.parser ParserException]
           [schema.utils ValidationError NamedError]))

;;
;; Exceptions handling
;;

(defn- safe-handler
  "Prints stacktrace to console and returns safe error response.
   Error response only contains class of the Exception so that it won't accidentally
   expose secret details."
  [^Exception e _ _]
  (logging/log! :error e (.getMessage e))
  (r/internal-server-error {:type "unknown-exception"
                            :class (.getName (.getClass e))}))

(defn stringify
  "Stringifies symbols and validation errors in Schema error, keeping the structure intact."
  [error]
  (walk/postwalk
    (fn [x]
      (cond
        (instance? ValidationError x) (str (su/validation-error-explain x))
        (instance? NamedError x) (str (su/named-error-explain x))
        (symbol? x) (str x)
        :else x))
    error))

(defn coerce-error-handler [f]
  (fn [_ data _]
    (f (-> data
           (select-keys [:value :type :error :in #_:execution-id #_:stage])
           (update-in [:error] #(stringify %))))))

(def ^:private missing-route-handler (constantly (r/not-found)))
(def ^:private request-validation-handler (coerce-error-handler r/bad-request))
(def ^:private response-validation-handler (coerce-error-handler r/internal-server-error))

(defn- request-parsing-handler
  [^Exception ex _ _]
  (let [cause (.getCause ex)]
    (r/bad-request {:type (cond
                            (instance? JsonParseException cause) "json-parse-exception"
                            (instance? ParserException cause) "yaml-parse-exception"
                            :else "parse-exception")
                    :message (.getMessage cause)})))

(defn wrap-exceptions
  "Catches all exceptions and delegates to right error handler accoring to :type of Exceptions
    :handlers  - a map from exception type to handler
    :default   - default handler for everything no caught by handlers"
  [handler {:keys [handlers default]}]
  (let [default-handler (or default safe-handler)]
    (assert (fn? default-handler) "Default exception handler must be a function.")
    (fn [request]
      (slingshot/try+
        (handler request)
        (catch (get % :type) {:keys [type] :as data}
          (let [handler (get handlers type default-handler)]
            (handler (:throwable &throw-context) data request)))
        (catch Object _
          (default-handler (:throwable &throw-context) nil request))))))

;;
;; ring-middleware-format stuff
;;

(def ^:private +mime-types+
  {:json "application/json"
   :json-kw "application/json"
   :edn "application/edn"
   :yaml "application/x-yaml"
   :yaml-kw "application/x-yaml"
   :transit-json "application/transit+json"
   :transit-msgpack "application/transit+msgpack"})

(defn- mime-types [formats] (keep +mime-types+ formats))

(defn- handle-req-error [^Throwable e _ _]
  ;; Ring-middleware-format catches all exceptions in req handling,
  ;; i.e. (handler req) is inside try-catch. If r-m-f was changed to catch only
  ;; exceptions from parsing the request, we wouldn't need to check the exception class.
  (if (or (instance? JsonParseException e) (instance? ParserException e))
    (slingshot/throw+ {:type :kekkonen.ring/parsing} e)
    (slingshot/throw+ e)))

;;
;; Keyword params
;;

(defn wrap-keyword-keys [handler path]
  (fn [request]
    (handler (update-in request path walk/keywordize-keys))))

;;
;; Not Found
;;

(defn wrap-not-found [handler f]
  (fn [request]
    (let [response (handler request)]
      (if (and (not response) f)
        (f request)
        response))))

;;
;; api info
;;

(s/defn api-info [options]
  (let [mime-types (mime-types (some-> options :format :formats))]
    {:produces mime-types
     :consumes mime-types}))

;;
;; Api Middleware
;;

(def +default-options+
  {:format {:formats [:json-kw :yaml-kw :edn :transit-json :transit-msgpack]
            :params-opts {}
            :response-opts {}}
   :not-found missing-route-handler
   :exceptions {:default safe-handler
                :handlers {:kekkonen.core/dispatch missing-route-handler
                           :kekkonen.ring/request request-validation-handler
                           :kekkonen.ring/parsing request-parsing-handler
                           :kekkonen.ring/response response-validation-handler}}})

(defn wrap-api
  "Opinionated chain of middlewares for web apis. Takes options-map to configure
   all the needed middlewares. See details and defaults from the source.

   Accepts the following options:

   - :exceptions                options for kekkonen.core/wrap-exceptions
     - :handlers                - map of type->exception-handler for exceptions. exception-handlers
                                  take 3 arguments: the exception, ExceptionInfo data and the originating request.
                                  tip: to catch normal Schema errors use :schema.core/error as type

   - :not-found                 a function request=>response to handle nil responses

   - :format                    options for ring-middleware-format middlewares
     - :formats                 - sequence of supported formats, e.g. [:json-kw :edn]
     - :params-opts             - for ring.middleware.format-params/wrap-restful-params,
                                  e.g. {:transit-json {:handlers readers}}
     - :response-opts           - for *ring.middleware.format-params/wrap-restful-response*,
                                  e.g. {:transit-json {:handlers writers}}"
  ([handler]
   (wrap-api handler {}))
  ([handler options]
   (let [options (kc/deep-merge +default-options+ options)
         {:keys [exceptions format]} options
         {:keys [formats params-opts response-opts]} format]
     (-> handler
         ring.middleware.http-response/wrap-http-response
         (wrap-not-found (:not-found options))
         (wrap-restful-params
           {:formats formats
            :handle-error handle-req-error
            :format-options params-opts})
         (wrap-exceptions exceptions)
         (wrap-restful-response
           {:formats formats
            :format-options response-opts})
         (wrap-keyword-keys [:query-params])
         wrap-keyword-params
         wrap-nested-params
         wrap-params))))
