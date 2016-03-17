## 0.2.0-SNAPSHOT

* Change Transformers to Interceptors in both the Dispatcher & Ring.
* `:user` is now `:meta`.
* Defined `:meta` keys are checked at dispatcher creation time.
* Ring-adapter interceptors can use the dispatcher context, fixes [#26](https://github.com/metosin/kekkonen/issues/26)

* Updated dependencies

```clj
[prismatic/schema "1.0.5"] is available but we use "1.0.4"
[metosin/ring-swagger "0.22.5"] is available but we use "0.22.1"
[metosin/ring-swagger-ui "2.1.4-0"] is available but we use "2.1.3-4"
[clj-http "2.1.0"] is available but we use "2.0.0"
```

## 0.1.2 (30.12.2015)

* Fix client using wrong keywords [#16](https://github.com/metosin/kekkonen/pull/16)
* Deterministic order for user annotations [#15](https://github.com/metosin/kekkonen/pull/15)

```clj
[prismatic/schema "1.0.4"] is available but we use "1.0.3"
[metosin/ring-swagger "0.22.1"] is available but we use "0.22.0"
[metosin/ring-swagger-ui "2.1.3-4"] is available but we use "2.1.3-2"
```

## 0.1.1 (25.11.2015)

- Fix Transit format options

## 0.1.0 (10.11.2015)

- Initial public version
