"""NNDC (National Nuclear Data Center) integration module.

Provides utilities to fetch nuclear data from NNDC databases, including:
- Q-value and uncertainties
- Half-lives and spin/parity data
- Separation energy values (neutron/proton)
"""

from .nndc_client import NNDCClient, NNDCData, FetchError

__all__ = ['NNDCClient', 'NNDCData', 'FetchError']
