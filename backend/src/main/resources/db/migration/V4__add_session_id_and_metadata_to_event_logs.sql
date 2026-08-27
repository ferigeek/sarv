ALTER TABLE public.event_logs
    ADD COLUMN session_id uuid;

ALTER TABLE public.event_logs
    ADD COLUMN metadata jsonb;

COMMENT ON COLUMN public.event_logs.session_id
    IS 'Groups multiple user actions that happened during the same usage session; unrelated to JWT authentication';

COMMENT ON COLUMN public.event_logs.metadata
    IS 'Extra event-specific information that does not deserve its own database column';
