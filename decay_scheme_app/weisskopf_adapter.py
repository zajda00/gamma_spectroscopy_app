from __future__ import annotations

class WeisskopfAdapter:
    """Placeholder adapter for future integration with Weisskopf tooling.

    This class intentionally stays small in the first release. It defines the seam
    where future code can connect to an existing Weisskopf estimator package or CLI.
    """

    def available(self) -> bool:
        return False

    def estimate(self, *args, **kwargs):
        raise NotImplementedError('Weisskopf integration is not yet wired in this first desktop release.')
