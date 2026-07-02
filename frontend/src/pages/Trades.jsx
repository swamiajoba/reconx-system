// Compound DataTable.
// useDebouncedSearch.
import React, { useEffect, useState } from 'react';
import { withAuth } from '@components/withAuth.jsx';
import DataTable from '@components/DataTable.jsx';
import { useDebouncedSearch } from '@hooks/useDebouncedSearch.js';
import { api } from '@services/apiService.js';

function Trades() {
  const [search, setSearch] = useState('');
  const debounced = useDebouncedSearch(search, 300);
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ items: [], totalPages: 0 });

  useEffect(() => {
    const params = `?page=${page}&size=20${debounced ? `&status=${debounced}` : ''}`;
    api.listTrades(params).then(setData).catch(() => setData({ items: [], totalPages: 0 }));
  }, [page, debounced]);

  return (
    <section>
      <h2>Trades</h2>
      <input
        aria-label="Filter by status"
        placeholder="status filter (PENDING/MATCHED/…)"
        value={search}
        onChange={(e) => setSearch(e.target.value.toUpperCase())}
      />
      <DataTable>
        <DataTable.Header columns={[
          { key: 'tradeRef', label: 'Ref' },
          { key: 'symbol',   label: 'Symbol' },
          { key: 'qty',      label: 'Qty' },
          { key: 'price',    label: 'Price' },
          { key: 'status',   label: 'Status' },
        ]} />
        <DataTable.Body rows={data.items} render={(t) => (
          <>
            <span>{t.tradeRef}</span>
            <span>{t.instrumentSymbol}</span>
            <span>{t.quantity}</span>
            <span>{t.price}</span>
            <span>{t.status}</span>
          </>
        )} />
        <DataTable.Pagination
          page={page}
          totalPages={Math.max(1, data.totalPages)}
          onChange={setPage}
        />
      </DataTable>
    </section>
  );
}

export default withAuth(Trades);
