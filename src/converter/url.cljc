(ns converter.url
  (:require
   [cemerick.url :as url]
   #?(:clj [clojure.java.io :as io])
   [clojure.string :as string]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [converter.str :as str]
   #?(:cljs [file-uri-to-path])
   [spec-tools.core :as st]))

; TODO only accept file:// urls

; it is assumed that the string is already url-encoded
(defn string->url
  [_ str]
  (if (string? str)
    (try
      ; on cljs/node, cemerick/url seems to be doing url decoding for whitespace (%20), no idea why..
      ; so on cljs, whitespace is url encoded again
      #?(:clj (url/url str)
         :cljs (-> str url/url (update :path #(string/replace % " " "%20"))))
      (catch #?(:clj Exception, :cljs js/Error) _ str))
    str))

#?(:clj
   (defn url->path
     [url]
     (-> url
         str
         java.net.URL.
         io/as-file
         str)))

#?(:cljs
   (defn url->path
     [url]
     (-> url
         str
         file-uri-to-path
         ; FIXME why is file-uri-to-path not url decoding certain chars?
         (string/replace "%23" "#")
         (string/replace "%24" "$")
         (string/replace "%26" "&")
         (string/replace "%2B" "+")
         (string/replace "%2C" ",")
         (string/replace "%3F" "?"))))

(defn drive->wsl
  [url wsl?]
  (if wsl?
    (url/url url (string/replace (:path url)
                                 #"^/([A-Z]):/"
                                 #(str "/mnt/" (string/lower-case (% 1)) "/")))

    url))

(defn url-gen
  []
  (->> (gen/tuple
      ;; base
        (gen/elements #{"file://localhost"})
      ;; drive letter (optional)
        (gen/one-of [(str/drive-letter-gen) (gen/elements #{""})])
      ;; path
        (->> (str/not-blank-string-with-whitespace-gen)
             (gen/fmap url/url-encode)
             (gen/vector)
             (gen/not-empty)))
       (gen/fmap
        #(apply url/url (flatten %)))))

(s/def ::url (st/spec (s/and
                       #(instance? cemerick.url.URL %)
                       #(= "file" (:protocol %)))
                      {:type :url
                       :gen #(url-gen)}))

(defn file-ext
  [url]
  (->> url
      :path
      (re-find #"\.([^\.]+)$")
      second))