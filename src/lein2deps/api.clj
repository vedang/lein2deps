(ns lein2deps.api
  (:require
   [babashka.cli :as cli]
   [babashka.fs :as fs]
   [clojure.pprint :as pprint]
   [lein2deps.internal :refer [safe-parse convert-dep
                               add-prep-lib]]))

(defn lein2deps
  "Converts project.clj to deps.edn.

  Options:
  * `:project-clj` - defaults to `project.clj` in working directory. In case the specified file does not exist, the input is treated as a string.
  * `:eval` - evaluate code in `project.clj`. Defaults to `false`
  * `:write-file` - write `deps.edn` to specified file. Defaults to not writing.
  * `:print` - print `deps.edn` to stdout. Defaults to `true`."
  [opts]
  (let [project-clj (or (:project-clj opts)
                        "project.clj")
        project-clj-str (if (fs/exists? project-clj)
                          (slurp project-clj)
                          project-clj)
        project-edn (if (:eval opts)
                      (load-string project-clj-str)
                      (safe-parse project-clj-str))
        project-edn (merge {:compile-path "target/classes"
                            :source-paths ["src"]}
                           project-edn)
        {:keys [dependencies source-paths resource-paths compile-path java-source-paths]} project-edn
        deps-edn {:paths (cond-> (into (vec source-paths) resource-paths)
                           java-source-paths
                           (conj compile-path))
                  :deps (into {} (map convert-dep) dependencies)}
        deps-edn (cond-> deps-edn
                   java-source-paths
                   (add-prep-lib project-edn))]
    (when-let [f (:write-file opts)]
      (spit f (with-out-str (pprint/pprint deps-edn))))
    (when (:print opts)
      (pprint/pprint deps-edn))
    {:deps deps-edn }))

(defn -main [& args]
  (let [opts (cli/parse-opts args)]
    (if (:help opts)
      (println "Usage: lein2deps <opts>

Options:

  --project-clj <file>: defaults to \"project.clj\"
  --eval              : evaluate project.clj. Use at your own risk.")
      (do (lein2deps (merge {:print true} opts))
          nil))))