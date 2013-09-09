(ns brainbot.nozzle.extract2
  (:require [langohr.basic :as lb]
            [langohr.shutdown :as lshutdown]
            [langohr.exchange  :as le]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons])

  (:require [clojure.tools.logging :as logging])
  (:require [brainbot.nozzle.mqhelper :as mqhelper]
            [brainbot.nozzle.misc :as misc]
            [brainbot.nozzle.inihelper :as inihelper]
            [brainbot.nozzle.dynaload :as dynaload]
            [brainbot.nozzle.worker :as worker]
            [brainbot.nozzle.vfs :as vfs])
  (:require [brainbot.nozzle.extract :refer [wash convert]]))


(defn extract-content
  [local-file-path entry]
  (if-let [converted (convert local-file-path)]
    (assoc entry "tika-content" converted)
    entry))


(defn simple-extract_content
  [fs {directory :directory, {relpath :relpath :as entry} :entry, :as body} {publish :publish}]
  (let [path (vfs/join fs [directory relpath])
        extract (try
                  (vfs/extract-content fs path)
                  (catch Throwable err
                    (logging/error "error in extract-content" {:error err :path path})
                    nil))
        new-body (if extract
                   (assoc body :extract extract)
                   body)]
    (publish "import_file" new-body)))


(defn build-handle-connection
  [filesystems]
  (fn [conn]
    (logging/info "initializing connection")
    (dotimes [n 5]
      (doseq [{:keys [fsid] :as fs} filesystems]
        (mqhelper/channel-loop
         conn
         (fn [ch]
           (let [qname (mqhelper/initialize-rabbitmq-structures ch "extract_content" "nextbot" fsid)]
             (logging/info "starting consumer for" qname)
             ;; (lb/qos ch 1)
             (lcons/subscribe ch qname (mqhelper/make-handler (partial simple-extract_content fs))))))))))


(defrecord ExtractService [rmq-settings filesystems]
  worker/Service
  (start [this]
    (future (mqhelper/connect-loop-with-thread-pool
             rmq-settings
             (build-handle-connection filesystems)))))

(def runner
  (reify
    dynaload/Loadable
    inihelper/IniConstructor
    (make-object-from-section [this iniconfig section]
      (let [rmq-settings (inihelper/rmq-settings-from-config iniconfig)
            filesystems (vfs/make-filesystems-from-iniconfig iniconfig section)]

        (when (empty? filesystems)
          (misc/die (str "no filesystems defined in section " section)))
        (->ExtractService rmq-settings filesystems)))))
