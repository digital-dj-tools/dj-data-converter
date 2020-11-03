#?(:clj (set! *warn-on-reflection* true))
(ns converter.spec
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.zip :as zip]
   [converter.time :as time]
   [converter.url :as url]
   [spec-tools.core :as st]
   [spec-tools.transform :as stt]))

; TODO can't be implemented yet, if encoded value is invalid, 
; there's no way to coerce using encoder rather than decoder
; (defn encode!
;   [spec value transformer])

(defn decode!
  [spec value transformer]
  ; st/conform! is used instead of st/decode, because currently st/coerce 
  ; (and therefore also st/decode) can't coerce for specs that use s/cat and regex ops
  ; https://github.com/metosin/spec-tools/issues/149
  (let [conformed (st/conform! spec value transformer)]
    (s/unform spec conformed)))

(defn xml-transformer
  ([]
   (xml-transformer "yyyy-MM-dd"))
  ([date-format]
   (st/type-transformer
    {:name :xml
     :decoders (merge stt/string-type-decoders {:url url/string->url
                                                :date (partial time/string->date date-format)})
     :encoders (merge stt/string-type-encoders {:url stt/any->string
                                                :date (partial time/date->string date-format)})
     :default-encoder stt/any->any})))

(defn string-transformer
  ([]
   (string-transformer "yyyy-MM-dd"))
  ([date-format]
   (st/type-transformer
    {:name :string
     :decoders (merge stt/string-type-decoders {:url url/string->url
                                                :date (partial time/string->date date-format)})
     :encoders (merge stt/string-type-encoders {:url stt/any->string
                                                :date (partial time/date->string date-format)})
     :default-encoder stt/any->any})))

; this only works as long as it's not nested inside other data specs
(defrecord XmlZipSpec [spec]
  s/Spec
  (conform* [_ v]
    (s/conform spec (zip/node v)))
  (unform* [_ v]
    (s/unform spec (zip/node v)))
  (explain* [_ path via in v]
    (s/explain* spec path via in (zip/node v)))
  (gen* [_ overrides path rmap]
    (gen/fmap #(zip/xml-zip %) (s/gen* spec overrides path rmap))))

(defn xml-zip-spec [spec]
  (->XmlZipSpec spec))

(defn such-that
  [spec pred]
  (s/with-gen spec 
    (fn [] (gen/such-that pred (s/gen spec)))))

; TODO spec is assumed to be a spec-tools record
(defn such-that-spec
  ([spec pred]
   (assoc spec :gen (fn [] (gen/such-that pred (s/gen spec)))))
  ([spec pred max-tries]
   (assoc spec :gen (fn [] (gen/such-that pred (s/gen spec) max-tries)))))

; TODO spec is assumed to be a spec-tools record
(defn with-gen-fmap-spec
  [spec f]
  (assoc spec :gen (fn [] (gen/fmap f (s/gen spec)))))

(defrecord ValueEncodedSpec [spec transformer]
  s/Spec
  (conform* [_ v]
    (st/conform spec v transformer))
  (unform* [_ v]
    (s/unform spec v))
  (explain* [_ path via in v]
    (prn "[ValueEncodedSpec/explain*] path: " path)
    (prn "[ValueEncodedSpec/explain*] via: " via)
    (prn "[ValueEncodedSpec/explain*] in: " in)
            ; TODO this isn't quite right, path, via and in are ignored.. need to duplicate spec-tools.core/Spec/explain*
    (st/explain spec v transformer))
  (gen* [_ overrides path rmap]
    (gen/fmap #(st/encode spec % transformer) (s/gen* spec overrides path rmap))))

(defn value-encoded-spec
  [spec transformer]
  (->ValueEncodedSpec spec transformer))
