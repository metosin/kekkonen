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
            [muuntaja.core :as muuntaja]
            [muuntaja.middleware]
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

(defn- handle-req-error [^Throwable e _ _]
  ;; Ring-middleware-format catches all exceptions in req handling,
  ;; i.e. (handler req) is inside try-catch. If r-m-f was changed to catch only
  ;; exceptions from parsing the request, we wouldn't need to check the exception class.
  (if (or (instance? JsonParseException e) (instance? ParserException e))
    (slingshot/throw+ {:type :kekkonen.ring/parsing} e)
    (slingshot/throw+ e)))

(defn create-muuntaja [options]
  (if options
    (muuntaja.core/create
      (->
        (if (= ::defaults options)
          muuntaja.core/default-options
          options)))))
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
  {:produces (some-> options :formats :produces)
   :consumes (some-> options :formats :consumes)})

;;
;; Api Middleware
;;

(def +default-options+
  {:formats ::defaults
   :not-found missing-route-handler
   :exceptions {:default safe-handler
                :handlers {:kekkonen.core/dispatch missing-route-handler
                           :kekkonen.core/request request-validation-handler
                           :kekkonen.core/response response-validation-handler
                           ::muuntaja/decode request-parsing-handler
                           :kekkonen.ring/parsing request-parsing-handler
                           :kekkonen.ring/request request-validation-handler
                           :kekkonen.ring/response response-validation-handler}}})

(defn wrap-api
  "Opinionated chain of middlewares for web apis. Takes options-map to configure
   all the needed middlewares. See details and defaults from the source.

   Accepts the following options:

   - :exceptions        options for kekkonen.core/wrap-exceptions
     - :handlers        - map of type->exception-handler for exceptions. exception-handlers
                        take 3 arguments: the exception, ExceptionInfo data and the originating request.
                        tip: to catch normal Schema errors use :schema.core/error as type

   - :not-found         a function request=>response to handle nil responses

   - :formats           a compiled Muuntaja, ::defaults or muuntaja options"
  ([handler]
   (wrap-api handler {}))
  ([handler options]

   (assert
     (not (contains? options :format))
     (str "ERROR: Option [:format] is not used with 0.4.0 or later. Kekkonen uses now Muuntaja insted of"
          "ring-middleware-format and the new formatting options for it should be under [:formats]. See "
          "'(doc kekkonen.middleware/wrap-api)' for more details."))

   (let [options (kc/deep-merge +default-options+ options)
         {:keys [exceptions formats]} options
         muuntaja (create-muuntaja formats)]
     (-> handler
         ring.middleware.http-response/wrap-http-response
         (wrap-not-found (:not-found options))
         (muuntaja.middleware/wrap-format-request muuntaja)
         (wrap-exceptions exceptions)
         (muuntaja.middleware/wrap-format-response muuntaja)
         (muuntaja.middleware/wrap-format-negotiate muuntaja)
         (wrap-keyword-keys [:query-params])
         wrap-keyword-params
         wrap-nested-params
         wrap-params))))
