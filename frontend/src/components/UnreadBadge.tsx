interface Props {
  count: number;
}

export const UnreadBadge = ({ count }: Props) => {
  if (count <= 0) return null;

  const display = count > 99 ? '99+' : String(count);

  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      minWidth: 18,
      height: 18,
      padding: '0 5px',
      borderRadius: 9,
      backgroundColor: '#ff4d4f',
      color: '#fff',
      fontSize: 11,
      fontWeight: 600,
      lineHeight: 1,
    }}>
      {display}
    </span>
  );
};
