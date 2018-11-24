(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.core :as c]
   [converter.rekordbox.core :as r]
   [converter.traktor.core :as t]
   [converter.spec :as spec]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/def ::check-input boolean?)

(s/fdef convert
  :args (s/cat :nml-xml (spec/value-encoded-spec t/nml-xml-spec spec/string-transformer)
               :options (s/keys))
  :ret (spec/value-encoded-spec r/dj-playlists-xml-spec spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [xml options]
  (if
   (and (:check-input options) (s/invalid? (st/conform t/nml-xml-spec xml spec/string-transformer)))
   (let [explain (st/explain-data t/nml-xml-spec xml spec/string-transformer)
         data {:type ::convert
               :problems (st/+problems+ explain)
               :spec t/nml-xml-spec
               :value xml}]
     (throw (ex-info "Spec conform error:" data))))
  (as-> xml $
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first next next :content)))
    (spec/decode! t/nml-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) ::t/collection)))
    (c/traktor->rekordbox $)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) ::r/collection)))
    (st/encode r/dj-playlists-xml-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first :content)))
    ))