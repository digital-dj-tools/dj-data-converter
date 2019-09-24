(ns converter.traktor.nml
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.str :as str]))

(def nml-path-sep
  "/:")

(def nml-path-sep-regex
  #"/:")

(defn nml-dir
  [dirs]
  (as-> dirs $
    (interleave (repeat nml-path-sep) $)
    (vec $)
    (conj $ nml-path-sep)
    (apply str $)))

(defn nml-dir-gen
  []
  (gen/fmap nml-dir (gen/vector (str/not-blank-string-with-whitespace-gen))))

(def nml-dir-regex
  #"(?:/:.+)*/:")

(s/def ::nml-dir
  (s/with-gen
    (s/and string? #(re-matches nml-dir-regex %))
    (fn [] (nml-dir-gen))))

(s/def ::nml-path
  (s/with-gen
    string? ; TODO and with cat+regex specs
    (fn [] (gen/fmap (partial apply str)
                     (gen/tuple
                      ; drive letter (optional)
                      (gen/one-of [(str/drive-letter-gen) (gen/elements #{""})])
                      ; dir
                      (nml-dir-gen)
                      ; filename
                      (str/not-blank-string-with-whitespace-gen))))))